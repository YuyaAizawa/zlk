package zlk.nameeval;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import zlk.ast.AnType;
import zlk.ast.Constructor;
import zlk.ast.Decl;
import zlk.ast.Decl.TypeAlias;
import zlk.ast.Decl.TypeDecl;
import zlk.common.RecordField;
import zlk.common.Type;
import zlk.common.Type.Row;
import zlk.common.Type.RowVar;
import zlk.common.id.Id;
import zlk.common.id.IdMap;
import zlk.util.collection.Seq;
import zlk.util.collection.SeqBuffer;

/**
 * ASTの型構文をsemantic {@link Type}へ解決する．
 *
 * <p>{@link NameEvaluator}は，次の順序でこのresolverを使用する．
 * <ol>
 *   <li>全{@link TypeDecl}／{@link TypeAlias}の名前を{@link #register(Seq, Id)}で登録する．
 *       宣言本体より先に全型名を登録することで，型宣言間の前方参照を可能にする．</li>
 *   <li>{@link #resolveAliases()}で全alias本体を解決する．
 *       aliasはDFSで展開し，循環を拒否するとともに，展開後のtemplateと各parameterのkindをcacheする．</li>
 *   <li>値名前環境がconstructorの{@link Id}を割り当てた後，
 *       {@link #resolveConstructor(TypeDecl, Constructor, Id)}でconstructor引数を一度だけ解決する．
 *       その結果を値としてのconstructor signatureと{@code IcCtor}の引数型で共有する．</li>
 *   <li>値注釈は{@link #evalAnnotation(AnType)}で解決する．</li>
 * </ol>
 *
 * <p>型名から{@link Id}への対応，Id基準の宣言情報，nominal型，alias解決cache，
 * constructor解決結果はそれぞれ別の表で保持する．型変数は評価箇所に応じて，
 * 暗黙導入を許す値注釈，宣言済みparameterだけを許すADT，TYPE／ROW kindを
 * 本体から決定するaliasのいずれかの文脈で検査する．alias Idは全型名の登録を通して収集・保持する．
 */
final class TypeResolver {
	private final TypeEnv typeEnv;
	private final IdMap<TyDeclInfo> declInfos = new IdMap<>();
	private final IdMap<Type> nominalTypes = new IdMap<>();
	private final IdMap<AliasState> aliasStates = new IdMap<>();
	private Seq<Id> aliasIds = null;
	private final IdMap<ResolvedConstructor> resolvedConstructors = new IdMap<>();

	TypeResolver() {
		this.typeEnv = new TypeEnv();
		Type.BUILTIN.forEach(ty -> {
			try {
				typeEnv.register(ty.id().simpleName(), ty.id());
				declInfos.put(ty.id(), new TyDeclInfo.Nominal(ty.id(), 0));
				nominalTypes.put(ty.id(), ty);
			} catch (DuplicatedNameException e) {
				throw new Error("builtin type dupicated", e);
			}
		});
	}

	/** 全型名を登録し，alias Idを宣言順のimmutableな列として保持する． */
	void register(Seq<Decl> decls, Id owner) {
		if (aliasIds != null) {
			throw new IllegalStateException("type names are already registered");
		}

		SeqBuffer<Id> aliases = new SeqBuffer<>();
		decls.forEach(decl -> {
			switch (decl) {
			case TypeDecl typeDecl -> register(typeDecl, owner);
			case TypeAlias alias -> aliases.add(register(alias, owner));
			default -> {}
			}
		});
		aliasIds = aliases.toSeq();
	}

	private void register(TypeDecl decl, Id owner) {
		Id id = Id.intern(owner, decl.name());
		try {
			typeEnv.register(decl.name(), id);
			declInfos.put(id, new TyDeclInfo.Nominal(id, decl.vars().size()));
		} catch (DuplicatedNameException e) {
			throw new RuntimeException(e);
		}
		Seq<Type> tyArgs = decl.vars().map(v -> (Type) new Type.Var(v.name()));
		nominalTypes.put(id, new Type.CtorApp(id, tyArgs));
	}

	private Id register(TypeAlias decl, Id owner) {
		Id id = Id.intern(owner, decl.name());
		validateUniqueParameters(decl);
		AliasDecl aliasDecl = new AliasDecl(decl.vars(), decl.body());
		try {
			typeEnv.register(decl.name(), id);
			declInfos.put(id, new TyDeclInfo.Alias(id, decl.vars().size(), aliasDecl));
			aliasStates.put(id, new AliasState());
		} catch (DuplicatedNameException e) {
			throw new RuntimeException(e);
		}
		return id;
	}

	/** 全aliasをDFSで解決する． */
	void resolveAliases() {
		if (aliasIds == null) {
			throw new IllegalStateException("type names are not registered");
		}
		for (Id aliasId : aliasIds) {
			AliasState state = aliasStates.get(aliasId);
			if (state.status == AliasState.Status.UNVISITED) {
				TyDeclInfo.Alias alias = (TyDeclInfo.Alias) declInfos.get(aliasId);
				resolveAlias(alias);
			}
		}
	}

	/** 型名からIdを取得する．未登録の場合はNoSuchElementException． */
	Id getTypeId(String name) {
		return typeEnv.get(name);
	}

	/** constructor引数型と値としてのsignatureを一度だけ解決する． */
	ResolvedConstructor resolveConstructor(TypeDecl decl, Constructor ctor, Id ctorId) {
		TypeVarContext ctx = new TypeVarContext.Declared(decl.vars());
		Seq<Type> args = ctor.args().map(ty -> evalAsType(ty, ctx));
		Id typeId = typeEnv.get(decl.name());
		Type retTy = nominalTypes.get(typeId);
		Type signature = Type.fromSeq(Seq.concat(args, Seq.of(retTy)));
		ResolvedConstructor resolved = new ResolvedConstructor(ctorId, args, signature);
		resolvedConstructors.put(ctorId, resolved);
		return resolved;
	}

	/** 解決済みconstructorをctor Idから取得する．eval(TypeDecl)でIcCtor生成に使う． */
	ResolvedConstructor getResolvedConstructor(Id ctorId) {
		return resolvedConstructors.get(ctorId);
	}

	/** value annotation型を評価する．未宣言変数は暗黙導入する． */
	Type evalAnnotation(AnType aTy) {
		return evalAsType(aTy, new TypeVarContext.Implicit());
	}

	/** nominal型の安定Typeを取得する． */
	Type getType(Id id) {
		return nominalTypes.get(id);
	}

	private static void validateUniqueParameters(TypeAlias decl) {
		HashSet<String> seen = new HashSet<>();
		for (AnType.Var var : decl.vars()) {
			if (!seen.add(var.name())) {
				throw new IllegalArgumentException(
						"duplicated type alias parameter: " + var.name()
								+ " in alias " + decl.name());
			}
		}
	}

	/** alias本体をDFS解決し，循環検出用stateと解決結果をcacheする． */
	private void resolveAlias(TyDeclInfo.Alias alias) {
		AliasState state = aliasStates.get(alias.id());
		switch (state.status) {
		case RESOLVED -> { return; }
		case VISITING -> { throw new IllegalStateException("recursive type alias: " + alias.id().simpleName()); }
		case UNVISITED -> {
			AliasDecl decl = alias.decl();
			state.status = AliasState.Status.VISITING;
			try {
				TypeVarContext.AliasParams ctx = new TypeVarContext.AliasParams(decl.params());
				Type template = evalAsType(decl.body(), ctx);
				state.template = template;
				state.paramKinds = ctx.finalizeKinds();
				state.status = AliasState.Status.RESOLVED;
			} catch (RuntimeException | Error e) {
				// 各DFS frameが自分のstateとcacheを復旧する．
				state.status = AliasState.Status.UNVISITED;
				state.template = null;
				state.paramKinds = null;
				throw e;
			}
		}
		}
	}

	private Type applyAlias(TyDeclInfo.Alias alias, Seq<AnType> args, TypeVarContext ctx) {
		resolveAlias(alias);
		AliasDecl decl = alias.decl();
		AliasState state = aliasStates.get(alias.id());

		if (decl.params().size() != args.size()) {
			throw new IllegalArgumentException(
					"arity mismatch for type alias " + alias.id().simpleName()
					+ ": expected " + decl.params().size() + ", got " + args.size());
		}

		Map<String, Type> typeBinds = new HashMap<>();
		Map<String, Row> rowBinds = new HashMap<>();

		// 解決済みparamKindsは宣言順に整列したSeq<Kind>であり，
		// index iでparamKinds.at(i)を参照する．
		for (int i = 0; i < decl.params().size(); i++) {
			String paramName = decl.params().at(i).name();
			Kind paramKind = state.paramKinds.at(i);
			AnType arg = args.at(i);

			if (paramKind == Kind.ROW) {
				Row row = evalAsRow(arg, ctx);
				rowBinds.put(paramName, row);
			} else {
				Type t = evalAsType(arg, ctx);
				typeBinds.put(paramName, t);
			}
		}

		return substituteAlias(state.template, typeBinds, rowBinds);
	}

	private Type substituteAlias(Type template, Map<String, Type> typeBinds, Map<String, Row> rowBinds) {
		return switch (template) {
		case Type.Var(String name) -> {
			Type bound = typeBinds.get(name);
			yield bound != null ? bound : template;
		}
		case Type.CtorApp(Id id, Seq<Type> args) ->
			new Type.CtorApp(id, args.map(t -> substituteAlias(t, typeBinds, rowBinds)));
		case Type.Arrow(Type arg, Type ret) ->
			new Type.Arrow(
				substituteAlias(arg, typeBinds, rowBinds),
				substituteAlias(ret, typeBinds, rowBinds));
		case Type.Record(Row row) -> {
			Seq<RecordField<Type>> fields = row.fields().map(f ->
				new RecordField<>(f.name(), substituteAlias(f.value(), typeBinds, rowBinds)));
			Optional<RowVar> ext = row.extension();
			if (ext.isPresent()) {
				String extName = ext.orElseThrow().name();
				Row boundRow = rowBinds.get(extName);
				if (boundRow != null) {
					// prefix fields + argument row fields をflatten/canonicalize
					Seq<RecordField<Type>> merged = Seq.concat(fields, boundRow.fields());
					yield new Type.Record(new Row(merged, boundRow.extension()));
				}
			}
			yield new Type.Record(new Row(fields, ext));
		}
		};
	}

	/** AnTypeをTYPE文脈で評価してsemantic Typeを返す */
	private Type evalAsType(AnType aTy, TypeVarContext ctx) {
		return switch (aTy) {
		case AnType.Unit _ -> Type.UNIT;
		case AnType.Var(String name, _) -> {
			ctx.use(name, Kind.TYPE);
			yield new Type.Var(name);
		}
		case AnType.Type(String ctor, Seq<AnType> args, _) -> {
			Id id = typeEnv.getOrNull(ctor);
			if (id == null) {
				throw new IllegalArgumentException("unknown type: " + ctor);
			}
			TyDeclInfo info = declInfos.get(id);
			if (info.arity() != args.size()) {
				throw new IllegalArgumentException(
						"arity mismatch for " + ctor
						+ ": expected " + info.arity() + ", got " + args.size());
			}
			if (info instanceof TyDeclInfo.Alias alias) {
				yield applyAlias(alias, args, ctx);
			} else {
				Seq<Type> argTypes = args.map(a -> evalAsType(a, ctx));
				yield new Type.CtorApp(id, argTypes);
			}
		}
		case AnType.Arrow(AnType arg, AnType ret, _) ->
			new Type.Arrow(evalAsType(arg, ctx), evalAsType(ret, ctx));
		case AnType.Record(Optional<AnType.Var> extension, Seq<AnType.RecordField> fields, _) -> {
			Seq<RecordField<Type>> fieldTypes = fields.map(f ->
					new RecordField<>(f.name(), evalAsType(f.type(), ctx)));
			Optional<RowVar> ext = extension.map(v -> {
				ctx.use(v.name(), Kind.ROW);
				return new RowVar(v.name());
			});
			yield new Type.Record(new Row(fieldTypes, ext));
		}
		};
	}

	/** AnTypeをROW文脈で評価してRowを返す */
	private Row evalAsRow(AnType aTy, TypeVarContext ctx) {
		switch (aTy) {
		case AnType.Record(Optional<AnType.Var> ext, Seq<AnType.RecordField> fields, _):
			Seq<RecordField<Type>> fieldTypes = fields.map(f ->
					new RecordField<>(f.name(), evalAsType(f.type(), ctx)));
			Optional<RowVar> ext2 = ext.map(v -> {
				ctx.use(v.name(), Kind.ROW);
				return new RowVar(v.name());
			});
			return new Row(fieldTypes, ext2);
		case AnType.Var(String name, _):
			// Foo aでROW期待位置のAnType.Var aはRecord([], RowVar(a))相当
			ctx.use(name, Kind.ROW);
			return new Row(Seq.of(), Optional.of(new RowVar(name)));
		default:
			Type t = evalAsType(aTy, ctx);
			if (t instanceof Type.Record rec) {
				return rec.row();
			}
			throw new IllegalArgumentException(
					"ROW argument must be a record, but got: " + t);
		}
	}

	/** Idをkeyとする型宣言の意味情報． */
	private sealed interface TyDeclInfo permits TyDeclInfo.Nominal, TyDeclInfo.Alias {
		Id id();
		int arity();

		record Nominal(Id id, int arity) implements TyDeclInfo {}
		record Alias(Id id, int arity, AliasDecl decl) implements TyDeclInfo {}
	}

	/** DFS stateやcacheを含まないalias宣言． */
	private record AliasDecl(
			Seq<AnType.Var> params,
			AnType body) {}

	/** alias宣言とは分離して保持するmutableなDFS stateとcache． */
	private static final class AliasState {
		private enum Status { UNVISITED, VISITING, RESOLVED }

		private Status status = Status.UNVISITED;
		private Type template;
		private Seq<Kind> paramKinds;
	}

	private enum Kind { TYPE, ROW }

	/**
	 * 評価箇所ごとの型変数宣言規則とkind整合性を検査する文脈．
	 * 型構文を評価している場所に応じて，型変数の宣言可否とTYPE／ROW kindを検査し，
	 * aliasの場合は適用時に必要なkind情報も確定する
	 */
	static sealed abstract class TypeVarContext {
		final Map<String, Kind> kinds = new HashMap<>();
		/**
		 * 指定した名前が指定した文脈で利用されることを記録する．
		 * @param name 識別子の名前
		 * @param kind 出現した文脈
		 * @throws IllegalArgumentException 整合しない文脈で利用される場合
		 */
		abstract void use(String name, Kind kind);

		/** 値注釈では型変数を暗黙導入する． */
		private static final class Implicit extends TypeVarContext {
			@Override
			public void use(String name, Kind kind) {
				Kind existing = kinds.get(name);
				if (existing != null && existing != kind) {
					throw new IllegalArgumentException(
							"kind conflict for variable " + name + ": " + existing + " vs " + kind);
				}
				kinds.put(name, kind);
			}
		}

		/** ADTでは宣言済みparameterだけをTYPEとして使用できる． */
		private static final class Declared extends TypeVarContext {
			Declared(Seq<AnType.Var> vars) {
				super();
				for (AnType.Var var : vars) {
					if (kinds.putIfAbsent(var.name(), Kind.TYPE) != null) {
						throw new IllegalArgumentException(
								"duplicated type parameter: " + var.name());
					}
				}
			}

			@Override
			public void use(String name, Kind kind) {
				Kind existing = kinds.get(name);
				if (existing == null) {
					throw new IllegalArgumentException(
							"undeclared type variable in ADT constructor: " + name);
				}
				if (existing != kind) {
					throw new IllegalArgumentException(
							"kind conflict for variable " + name + ": " + existing + " vs " + kind);
				}
			}
		}

		/** alias parameterのkindを本体での使用から決定する． */
		private static final class AliasParams extends TypeVarContext {
			private enum Status { UNKNOWN, TYPE, ROW }

			private final List<Entry> entries = new ArrayList<>();
			private final Map<String, Entry> byName = new HashMap<>();

			private AliasParams(Seq<AnType.Var> params) {
				for (AnType.Var var : params) {
					Entry entry = new Entry();
					entries.add(entry);
					byName.put(var.name(), entry);
				}
			}

			@Override
			public void use(String name, Kind kind) {
				Entry entry = byName.get(name);
				if (entry == null) {
					throw new IllegalArgumentException(
							"undeclared type variable in alias body: " + name);
				}
				Status expected = kind == Kind.ROW ? Status.ROW : Status.TYPE;
				if (entry.status != Status.UNKNOWN && entry.status != expected) {
					throw new IllegalArgumentException(
							"kind conflict for variable " + name + ": " + entry.status + " vs " + expected);
				}
				entry.status = expected;
			}

			private Seq<Kind> finalizeKinds() {
				SeqBuffer<Kind> kinds = new SeqBuffer<>();
				for (Entry entry : entries) {
					kinds.add(entry.status == Status.ROW ? Kind.ROW : Kind.TYPE);
				}
				return kinds.toSeq();
			}

			private static final class Entry {
				private Status status = Status.UNKNOWN;
			}
		}
	}
}

/**
 * 一度だけ解決したADT constructorの意味情報．値としてのsignatureと
 * {@code IcCtor}の引数型は，同じインスタンスの結果を使用する．
 * {@link Constructor#loc()}はASTから取得できるため保持しない．
 */
record ResolvedConstructor(
		Id ctorId,
		Seq<Type> args,
		Type signature) {}
