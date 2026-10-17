package zlk.compiler.presentation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import zlk.compiler.id.Id;
import zlk.compiler.ir.typing.RecordField;
import zlk.compiler.ir.typing.Type;
import zlk.compiler.ir.typing.TypeAlias;
import zlk.compiler.phase.recon.TypeMatcher;
import zlk.util.collection.Seq;

/** 推論型をalias適用を含む印字専用のTypeへ変換する．書式はType.mkStringへ委譲する． */
public final class TypePrinter {
	private final Seq<TypeAlias> aliases;

	public TypePrinter(Seq<TypeAlias> aliases) {
		this.aliases = aliases;
	}

	/**
	 * 項そのものの型を読み，印字専用の型を返す．元の型は変更しない．
	 * 結果のCtorAppにはaliasも含むため，推論やcodegenへ戻さない．
	 */
	public Type convert(Type type) {
		return new Conversion(type).convert(type, new HashSet<>());
	}

	private record Site(Id alias, Type type) {}

	private final class Conversion {
		private final Set<String> used = new HashSet<>();
		private final Set<String> generated = new HashSet<>();

		Conversion(Type target) { reserve(target); }

		private void reserve(Type type) {
			switch (type) {
			case Type.Var(var name) -> used.add(name);
			case Type.CtorApp(_, var args) -> args.forEach(this::reserve);
			case Type.Arrow(var arg, var ret) -> { reserve(arg); reserve(ret); }
			case Type.Record(var row) -> {
				row.extension().ifPresent(ext -> used.add(ext.name()));
				row.fields().forEach(field -> reserve(field.value()));
			}
			}
		}

		private String freshName() {
			int index = 0;
			String name;
			do {
				name = index < 26 ? String.valueOf((char)('a' + index)) : "ty" + index;
				index++;
			} while (!used.add(name));
			generated.add(name);
			return name;
		}

		private Type rename(Type type, Map<String, String> names) {
			return switch (type) {
			case Type.Var(var name) -> new Type.Var(names.getOrDefault(name, name));
			case Type.CtorApp(var id, var args) -> new Type.CtorApp(id, args.map(arg -> rename(arg, names)));
			case Type.Arrow(var arg, var ret) -> new Type.Arrow(rename(arg, names), rename(ret, names));
			case Type.Record(var row) -> new Type.Record(rename(row, names));
			};
		}

		private Type.Row rename(Type.Row row, Map<String, String> names) {
			return new Type.Row(row.fields().map(field -> new RecordField<>(field.name(), rename(field.value(), names))),
					row.extension().map(ext -> new Type.RowVar(names.getOrDefault(ext.name(), ext.name()))));
		}

		private Optional<TypeMatcher.Solution> applicable(TypeAlias alias, Type type, Set<Site> active) {
			if (active.contains(new Site(alias.id(), type))) return Optional.empty();
			if (type instanceof Type.Var variable && generated.contains(variable.name())) return Optional.empty();
			return TypeMatcher.match(alias.parameters(), alias.template(), type);
		}

		private void collectNames(Type type, Set<String> unbound, Map<String, String> names) {
			switch (type) {
			case Type.Var(var name) -> { if (unbound.contains(name)) names.computeIfAbsent(name, _ -> freshName()); }
			case Type.CtorApp(_, var args) -> args.forEach(arg -> collectNames(arg, unbound, names));
			case Type.Arrow(var arg, var ret) -> { collectNames(arg, unbound, names); collectNames(ret, unbound, names); }
			case Type.Record(var row) -> {
				row.extension().ifPresent(ext -> { if (unbound.contains(ext.name())) names.computeIfAbsent(ext.name(), _ -> freshName()); });
				row.fields().forEach(field -> collectNames(field.value(), unbound, names));
			}
			}
		}

		private Type convert(Type type, Set<Site> active) {
			var candidates = aliases.filter(alias -> applicable(alias, type, active).isPresent());
			var survivors = candidates.filter(alias -> !candidates.anyMatch(other ->
					TypeMatcher.matches(alias.template(), other.template())
					&& !TypeMatcher.matches(other.template(), alias.template())));
			if (!survivors.isEmpty()) {
				TypeAlias alias = survivors.head();
				var substitution = applicable(alias, type, active).orElseThrow();
				Map<String, String> names = new HashMap<>();
				// 選択した解だけを改名し，phantom変数へalias選択を再帰しない．
				for (var parameter : alias.parameters()) {
					Type value = parameter.kind() == TypeAlias.Kind.TYPE
							? substitution.types().get(parameter.name())
							: new Type.Record(substitution.rows().get(parameter.name()));
					collectNames(value, substitution.unboundNames(), names);
				}
				Site site = new Site(alias.id(), type);
				active.add(site);
				try {
					Seq<Type> args = alias.parameters().map(parameter -> parameter.kind() == TypeAlias.Kind.TYPE
							? convert(rename(substitution.types().get(parameter.name()), names), active)
							: rowArgument(rename(substitution.rows().get(parameter.name()), names), active));
					return new Type.CtorApp(alias.id(), args);
				} finally {
					active.remove(site);
				}
			}
			return switch (type) {
			case Type.Var _ -> type;
			case Type.CtorApp(var id, var args) -> new Type.CtorApp(id, args.map(arg -> convert(arg, active)));
			case Type.Arrow(var arg, var ret) -> new Type.Arrow(convert(arg, active), convert(ret, active));
			case Type.Record(var row) -> new Type.Record(convertRow(row, active));
			};
		}

		private Type rowArgument(Type.Row row, Set<Site> active) {
			if (row.fields().isEmpty() && row.extension().isPresent()) {
				return new Type.Var(row.extension().orElseThrow().name());
			}
			return new Type.Record(convertRow(row, active));
		}

		private Type.Row convertRow(Type.Row row, Set<Site> active) {
			return new Type.Row(row.fields().map(field ->
					new RecordField<>(field.name(), convert(field.value(), active))), row.extension());
		}
	}
}
