package zlk.compiler.phase.recon;

import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import zlk.compiler.ir.typing.RecordField;
import zlk.compiler.ir.typing.Type;
import zlk.compiler.ir.typing.TypeAlias;
import zlk.util.collection.Seq;

/** 安定した型だけを受け渡す独立照合．各試行のgraphと生成器は外へ共有しない． */
public final class TypeMatcher {
	private TypeMatcher() {}

	/** 未束縛名はこの解内のfresh変数を表す．印字時の文脈全体での改名は呼出し側の責務． */
	public record Solution(Map<String, Type> types, Map<String, Type.Row> rows, Set<String> unboundNames) {
		public Solution {
			types = Map.copyOf(types);
			rows = Map.copyOf(rows);
			unboundNames = Set.copyOf(unboundNames);
		}
	}

	public static boolean equivalent(Type first, Type second) {
		return matches(first, second) && matches(second, first);
	}

	public static boolean matches(Type template, Type target) {
		return match(Seq.of(), template, target).isPresent();
	}

	/** alias側をfresh flex，対象側を共有関係とkindを保ったskolemとして単一化する． */
	public static Optional<Solution> match(Seq<TypeAlias.Parameter> parameters, Type template, Type target) {
		return new Trial(parameters).match(template, target);
	}

	/** 一回の照合で使う生成器と両側の変数表．可変状態を外に漏らさないため． */
	private static final class Trial {
		private final FreshFlex fresh = new FreshFlex();
		private final Map<String, Variable> types = new LinkedHashMap<>();
		private final Map<String, Variable> rows = new LinkedHashMap<>();
		private final Map<String, Variable> targetTypes = new HashMap<>();
		private final Map<String, Variable> targetRows = new HashMap<>();

		private Trial(Seq<TypeAlias.Parameter> parameters) {
			for (var parameter : parameters) {
				var kind = parameter.kind() == TypeAlias.Kind.TYPE ? Variable.Kind.TYPE : Variable.Kind.ROW;
				(kind == Variable.Kind.TYPE ? types : rows).put(parameter.name(), fresh.getVariable(kind, 1));
			}
		}

		private Optional<Solution> match(Type template, Type target) {
			var alias = RcType.fromType(template,
					name -> types.computeIfAbsent(name, _ -> fresh.getVariable(Variable.Kind.TYPE, 1)),
					name -> rows.computeIfAbsent(name, _ -> fresh.getVariable(Variable.Kind.ROW, 1)));
			var actual = RcType.fromType(target,
					name -> targetTypes.computeIfAbsent(name, _ -> Variable.ofRigid(name)),
					name -> targetRows.computeIfAbsent(name, _ -> Variable.ofRigid(name, Variable.Kind.ROW)));
			var left = alias.toVariable(1);
			var right = actual.toVariable(1);
			try {
				Unify.unify(left, right, fresh, 1);
				if (left.occurs()) return Optional.empty();
			} catch (Mismatch mismatch) {
				return Optional.empty();
			}
			Projection projection = new Projection(targetTypes, targetRows);
			Map<String, Type> typeSolution = new LinkedHashMap<>();
			Map<String, Type.Row> rowSolution = new LinkedHashMap<>();
			types.forEach((name, variable) -> typeSolution.put(name, projection.type(variable)));
			rows.forEach((name, variable) -> rowSolution.put(name, projection.row(variable)));
			return Optional.of(new Solution(typeSolution, rowSolution, projection.unbound));
		}
	}

	/** root identityによるskolem復元と，全parameterで共有する未束縛変数の射影． */
	private static final class Projection {
		private final Map<VariableState, String> names = new IdentityHashMap<>();
		private final Set<String> used = new HashSet<>();
		private final Set<String> unbound = new HashSet<>();

		Projection(Map<String, Variable> types, Map<String, Variable> rows) {
			types.forEach((name, variable) -> names.put(variable.get(), name));
			rows.forEach((name, variable) -> names.put(variable.get(), name));
			used.addAll(types.keySet());
			used.addAll(rows.keySet());
		}

		private String name(Variable variable) {
			return names.computeIfAbsent(variable.get(), _ -> {
				String name;
				int index = 0;
				do { name = "fresh" + index++; } while (!used.add(name));
				unbound.add(name);
				return name;
			});
		}

		Type type(Variable variable) {
			return switch (variable.get().content) {
			case Content.FlexVar _, Content.RigidVar _ -> new Type.Var(name(variable));
			case Content.Structure(FlatType.CtorApp1(var id, var args)) -> new Type.CtorApp(id, args.map(this::type));
			case Content.Structure(FlatType.Fun1(var arg, var ret)) -> new Type.Arrow(type(arg), type(ret));
			case Content.Structure(FlatType.Record1(var row)) -> new Type.Record(row(row));
			default -> throw new IllegalStateException("expected a solved TYPE variable");
			};
		}

		Type.Row row(Variable variable) {
			return switch (variable.get().content) {
			case Content.FlexVar _, Content.RigidVar _ -> new Type.Row(Seq.of(), Optional.of(new Type.RowVar(name(variable))));
			case Content.Structure(FlatType.Row1(var fields, var extension)) -> {
				var prefix = fields.map(field -> new RecordField<>(field.name(), type(field.value())));
				var tail = extension.map(this::row).orElseGet(() -> new Type.Row(Seq.of()));
				yield new Type.Row(Seq.concat(prefix, tail.fields()), tail.extension());
			}
			default -> throw new IllegalStateException("expected a solved ROW variable");
			};
		}
	}
}
