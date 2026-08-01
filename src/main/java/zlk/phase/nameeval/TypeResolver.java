package zlk.phase.nameeval;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import zlk.common.RecordField;
import zlk.common.Type;
import zlk.common.Type.Row;
import zlk.common.Type.RowVar;
import zlk.common.id.Id;
import zlk.common.id.IdMap;
import zlk.ir.ast.AnType;
import zlk.ir.ast.Constructor;
import zlk.ir.ast.Decl;
import zlk.ir.ast.Decl.TypeAlias;
import zlk.ir.ast.Decl.TypeDecl;
import zlk.util.collection.Seq;
import zlk.util.collection.SeqBuffer;

/**
 * ASTの型構文をsemantic {@link Type}へ解決する．
 *
 * <p>{@link NameEvaluator}は，次の順序でこのresolverを使用する．
 * <ol>
 *   <li>{@link #resolveDeclarations(Seq, Id)}で全型名の登録，parameter kind制約の解決，
 *       alias本体のDFS解決を行う．宣言本体より先に全型名を登録することで，
 *       型宣言間の前方参照を可能にする．</li>
 *   <li>値名前環境がconstructorの{@link Id}を割り当てた後，
 *       {@link #registerConstructor(TypeDecl, Constructor, Id)}で引数型と戻り値型を解決してcacheし，
 *       値としてのconstructor signatureと{@code IcCtor}の引数型で共有する．</li>
 *   <li>値注釈は{@link #evalAnnotation(AnType)}で解決する．</li>
 * </ol>
 *
 * <p>型名から{@link Id}への対応，Id基準の宣言情報，nominal型，parameter kind制約，
 * alias解決cache，constructor解決結果はそれぞれ別の表で保持する．
 * 宣言parameterのkindはsemantic型評価前に確定し，評価中は値注釈の暗黙変数または
 * 宣言済みparameterの確定kindだけを検査する．型宣言Idとalias Idは全型名の登録を通して収集・保持する．
 */
final class TypeResolver {
	private final TypeEnv typeEnv;
	private final IdMap<TyDeclInfo> declInfos = new IdMap<>();
	private final IdMap<Type> nominalTypes = new IdMap<>();
	private final IdMap<ParameterKinds> parameterKinds = new IdMap<>();
	private final IdMap<AliasState> aliasStates = new IdMap<>();
	private Seq<Id> declarationIds = null;
	private Seq<Id> aliasIds = null;
	private final IdMap<ResolvedConstructor> resolvedConstructors = new IdMap<>();

	TypeResolver() {
		this.typeEnv = new TypeEnv();
		Type.BUILTIN.forEach(ty -> {
			try {
				typeEnv.register(ty.id().simpleName(), ty.id());
				declInfos.put(ty.id(), new TyDeclInfo.Nominal(ty.id(), Seq.of(), Seq.of()));
				ParameterKinds kinds = new ParameterKinds(Seq.of(), ty.id().simpleName());
				kinds.resolve();
				parameterKinds.put(ty.id(), kinds);
				nominalTypes.put(ty.id(), ty);
			} catch (DuplicatedNameException e) {
				throw new Error("builtin type dupicated", e);
			}
		});
	}

	/** 型宣言の登録，parameter kind解決，alias解決を順に実行する． */
	void resolveDeclarations(Seq<Decl> decls, Id owner) {
		register(decls, owner);
		resolveParameterKinds();
		resolveAliases();
	}

	/** 全型名を登録し，型宣言Idとalias Idを宣言順のimmutableな列として保持する． */
	private void register(Seq<Decl> decls, Id owner) {
		if (aliasIds != null) {
			throw new IllegalStateException("type names are already registered");
		}

		SeqBuffer<Id> declarations = new SeqBuffer<>();
		SeqBuffer<Id> aliases = new SeqBuffer<>();
		decls.forEach(decl -> {
			switch (decl) {
			case TypeDecl typeDecl -> declarations.add(register(typeDecl, owner));
			case TypeAlias alias -> {
				Id id = register(alias, owner);
				declarations.add(id);
				aliases.add(id);
			}
			default -> {}
			}
		});
		declarationIds = declarations.toSeq();
		aliasIds = aliases.toSeq();
	}

	private Id register(TypeDecl decl, Id owner) {
		Id id = Id.intern(owner, decl.name());
		try {
			typeEnv.register(decl.name(), id);
			declInfos.put(id, new TyDeclInfo.Nominal(id, decl.vars(), decl.ctors()));
			parameterKinds.put(id, new ParameterKinds(decl.vars(), decl.name()));
		} catch (DuplicatedNameException e) {
			throw new RuntimeException(e);
		}
		return id;
	}

	private Id register(TypeAlias decl, Id owner) {
		Id id = Id.intern(owner, decl.name());
		try {
			typeEnv.register(decl.name(), id);
			declInfos.put(id, new TyDeclInfo.Alias(id, decl.vars(), decl.body()));
			parameterKinds.put(id, new ParameterKinds(decl.vars(), decl.name()));
			aliasStates.put(id, new AliasState());
		} catch (DuplicatedNameException e) {
			throw new RuntimeException(e);
		}
		return id;
	}

	/** 全型宣言からparameter kind制約を収集し，宣言順に確定する． */
	private void resolveParameterKinds() {
		if (declarationIds == null) {
			throw new IllegalStateException("type names are not registered");
		}

		for (Id id : declarationIds) {
			TyDeclInfo info = declInfos.get(id);
			KindScope scope = new KindScope(parameterKinds.get(id));
			switch (info) {
			case TyDeclInfo.Nominal nominal -> nominal.ctors().forEach(ctor ->
					ctor.args().forEach(arg -> constrainAsType(arg, scope)));
			case TyDeclInfo.Alias alias -> constrainAsType(alias.body(), scope);
			}
		}

		for (Id id : declarationIds) {
			parameterKinds.get(id).resolve();
		}
		for (Id id : declarationIds) {
			TyDeclInfo info = declInfos.get(id);
			if (info instanceof TyDeclInfo.Nominal nominal) {
				nominalTypes.put(id, new Type.CtorApp(
						id,
						parameterTypes(nominal.params(), kindsOf(id))));
			}
		}
	}

	/** 全aliasをDFSで解決する． */
	private void resolveAliases() {
		if (aliasIds == null) {
			throw new IllegalStateException("type names are not registered");
		}
		for (Id id : declarationIds) {
			if (!parameterKinds.get(id).isResolved()) {
				throw new IllegalStateException("type parameter kinds are not resolved");
			}
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

	/** constructorの引数型と戻り値型を解決し，Idに対応付ける． */
	void registerConstructor(TypeDecl decl, Constructor ctor, Id ctorId) {
		Id typeId = typeEnv.get(decl.name());
		TypeVarContext ctx = new TypeVarContext.Declared(decl.vars(), kindsOf(typeId));
		Seq<Type> args = ctor.args().map(ty -> evalAsType(ty, ctx));
		resolvedConstructors.put(
				ctorId,
				new ResolvedConstructor(args, nominalTypes.get(typeId)));
	}

	/** constructor Idなら値としての型を返し，それ以外ならnullを返す． */
	Type getConstructorTypeOrNull(Id ctorId) {
		ResolvedConstructor resolved = resolvedConstructors.getOrNull(ctorId);
		if (resolved == null) {
			return null;
		}
		return Type.fromSeq(Seq.concat(resolved.args(), Seq.of(resolved.ret())));
	}

	/** IcCtor生成に使う解決済みconstructor引数型を返す． */
	Seq<Type> getConstructorArgs(Id ctorId) {
		return resolvedConstructors.get(ctorId).args();
	}

	/** value annotation型を評価する．未宣言変数は暗黙導入する． */
	Type evalAnnotation(AnType aTy) {
		return evalAsType(aTy, new TypeVarContext.Implicit());
	}

	/** nominal型の安定Typeを取得する． */
	Type getType(Id id) {
		return nominalTypes.get(id);
	}

	/** nominal宣言parameterをsemantic type argumentとして返す． */
	Seq<Type> getTypeParameters(Id id) {
		TyDeclInfo info = declInfos.get(id);
		return parameterTypes(info.params(), kindsOf(id));
	}

	/** alias本体をDFS解決し，循環検出用stateと解決結果をcacheする． */
	private void resolveAlias(TyDeclInfo.Alias alias) {
		AliasState state = aliasStates.get(alias.id());
		switch (state.status) {
		case RESOLVED -> { return; }
		case VISITING -> { throw new IllegalStateException("recursive type alias: " + alias.id().simpleName()); }
		case UNVISITED -> {
			state.status = AliasState.Status.VISITING;
			try {
				TypeVarContext.Declared ctx = new TypeVarContext.Declared(
						alias.params(), kindsOf(alias.id()));
				state.template = evalAsType(alias.body(), ctx);
				state.status = AliasState.Status.RESOLVED;
			} catch (RuntimeException | Error e) {
				// 各DFS frameが自分のstateとcacheを復旧する．
				state.status = AliasState.Status.UNVISITED;
				state.template = null;
				throw e;
			}
		}
		}
	}

	private Type applyAlias(TyDeclInfo.Alias alias, Seq<AnType> args, TypeVarContext ctx) {
		resolveAlias(alias);
		AliasState state = aliasStates.get(alias.id());

		if (alias.params().size() != args.size()) {
			throw new IllegalArgumentException(
					"arity mismatch for type alias " + alias.id().simpleName()
					+ ": expected " + alias.params().size() + ", got " + args.size());
		}

		Map<String, Type> typeBinds = new HashMap<>();
		Map<String, Row> rowBinds = new HashMap<>();

		Seq<Kind> paramKinds = kindsOf(alias.id());
		for (int i = 0; i < alias.params().size(); i++) {
			String paramName = alias.params().at(i).name();
			Kind paramKind = paramKinds.at(i);
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
				Seq<Type> argTypes = evalNominalArguments(info, args, ctx);
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

	private Seq<Type> evalNominalArguments(TyDeclInfo info, Seq<AnType> args, TypeVarContext ctx) {
		return Seq.zip(args, kindsOf(info.id())).map(
				(arg, kind) -> kind == Kind.ROW
						? new Type.Record(evalAsRow(arg, ctx))
						: evalAsType(arg, ctx));
	}

	private Seq<Kind> kindsOf(Id id) {
		return parameterKinds.get(id).resolved();
	}

	private static Seq<Type> parameterTypes(Seq<AnType.Var> params, Seq<Kind> kinds) {
		return Seq.zip(params, kinds).map(
				(param, kind) -> kind == Kind.ROW
						? new Type.Record(new Row(Seq.of(), Optional.of(new RowVar(param.name()))))
						: new Type.Var(param.name()));
	}

	private void constrainAsType(AnType type, KindScope scope) {
		switch (type) {
		case AnType.Unit _ -> {}
		case AnType.Var(String name, _) -> scope.require(name, Kind.TYPE);
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
			ParameterKinds target = parameterKinds.get(id);
			for (int i = 0; i < args.size(); i++) {
				constrainArgument(args.at(i), target.slotAt(i), scope);
			}
		}
		case AnType.Arrow(AnType arg, AnType ret, _) -> {
			constrainAsType(arg, scope);
			constrainAsType(ret, scope);
		}
		case AnType.Record(Optional<AnType.Var> extension, Seq<AnType.RecordField> fields, _) -> {
			extension.ifPresent(var -> scope.require(var.name(), Kind.ROW));
			fields.forEach(field -> constrainAsType(field.type(), scope));
		}
		}
	}

	private void constrainArgument(AnType arg, KindSlot expected, KindScope scope) {
		switch (arg) {
		case AnType.Var(String name, _) -> scope.link(name, expected);
		case AnType.Unit _, AnType.Arrow _ -> {
			expected.require(Kind.TYPE);
			constrainAsType(arg, scope);
		}
		case AnType.Type _, AnType.Record _ -> constrainAsType(arg, scope);
		}
	}

	/** Idをkeyとする型宣言の意味情報． */
	private sealed interface TyDeclInfo permits TyDeclInfo.Nominal, TyDeclInfo.Alias {
		Id id();
		Seq<AnType.Var> params();
		default int arity() { return params().size(); }

		record Nominal(Id id, Seq<AnType.Var> params, Seq<Constructor> ctors) implements TyDeclInfo {}
		record Alias(Id id, Seq<AnType.Var> params, AnType body) implements TyDeclInfo {}
	}

	private record ResolvedConstructor(Seq<Type> args, Type ret) {}

	/** alias宣言とは分離して保持するmutableなDFS stateとcache． */
	private static final class AliasState {
		private enum Status { UNVISITED, VISITING, RESOLVED }

		private Status status = Status.UNVISITED;
		private Type template;
	}

	private enum Kind { TYPE, ROW }
	private enum KindStatus { UNKNOWN, TYPE, ROW }

	/** 宣言parameterのkind制約を宣言横断で共有するslot． */
	private static final class KindSlot {
		private KindSlot parent = this;
		private KindStatus status = KindStatus.UNKNOWN;

		private KindSlot root() {
			if (parent != this) {
				parent = parent.root();
			}
			return parent;
		}

		private void require(Kind kind) {
			KindSlot root = root();
			KindStatus required = kind == Kind.ROW ? KindStatus.ROW : KindStatus.TYPE;
			if (root.status != KindStatus.UNKNOWN && root.status != required) {
				throw new IllegalArgumentException(
						"kind conflict: " + root.status + " vs " + required);
			}
			root.status = required;
		}

		private void link(KindSlot other) {
			KindSlot left = root();
			KindSlot right = other.root();
			if (left == right) {
				return;
			}
			if (left.status != KindStatus.UNKNOWN
					&& right.status != KindStatus.UNKNOWN
					&& left.status != right.status) {
				throw new IllegalArgumentException(
						"kind conflict: " + left.status + " vs " + right.status);
			}
			right.parent = left;
			if (left.status == KindStatus.UNKNOWN) {
				left.status = right.status;
			}
		}

		private Kind resolve() {
			KindSlot root = root();
			if (root.status == KindStatus.UNKNOWN) {
				root.status = KindStatus.TYPE;
			}
			return root.status == KindStatus.ROW ? Kind.ROW : Kind.TYPE;
		}
	}

	private static final class ParameterKinds {
		private final Seq<KindSlot> slots;
		private final Map<String, KindSlot> byName = new HashMap<>();
		private Seq<Kind> resolved;

		private ParameterKinds(Seq<AnType.Var> params, String declarationName) {
			SeqBuffer<KindSlot> slots = new SeqBuffer<>();
			for (AnType.Var param : params) {
				KindSlot slot = new KindSlot();
				if (byName.putIfAbsent(param.name(), slot) != null) {
					throw new IllegalArgumentException(
							"duplicated type parameter: " + param.name()
							+ " in " + declarationName);
				}
				slots.add(slot);
			}
			this.slots = slots.toSeq();
		}

		private KindSlot slot(String name) {
			KindSlot slot = byName.get(name);
			if (slot == null) {
				throw new IllegalArgumentException("undeclared type variable: " + name);
			}
			return slot;
		}

		private KindSlot slotAt(int index) {
			return slots.at(index);
		}

		private void resolve() {
			if (resolved == null) {
				resolved = slots.map(KindSlot::resolve);
			}
		}

		private boolean isResolved() {
			return resolved != null;
		}

		private Seq<Kind> resolved() {
			if (resolved == null) {
				throw new IllegalStateException("type parameter kinds are not resolved");
			}
			return resolved;
		}
	}

	private static final class KindScope {
		private final ParameterKinds params;

		private KindScope(ParameterKinds params) {
			this.params = params;
		}

		private void require(String name, Kind kind) {
			params.slot(name).require(kind);
		}

		private void link(String name, KindSlot target) {
			params.slot(name).link(target);
		}
	}

	/** semantic型評価時の変数導入規則と確定済みkindを検査する文脈． */
	static sealed abstract class TypeVarContext {
		final Map<String, Kind> kinds = new HashMap<>();

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

		/** ADTとaliasでは宣言済みparameterを確定済みkindで使用する． */
		private static final class Declared extends TypeVarContext {
			Declared(Seq<AnType.Var> vars, Seq<Kind> declaredKinds) {
				Seq.zip(vars, declaredKinds).forEach((var, kind) -> {
					if (kinds.putIfAbsent(var.name(), kind) != null) {
						throw new IllegalArgumentException(
								"duplicated type parameter: " + var.name());
					}
				});
			}

			@Override
			public void use(String name, Kind kind) {
				Kind existing = kinds.get(name);
				if (existing == null) {
					throw new IllegalArgumentException("undeclared type variable: " + name);
				}
				if (existing != kind) {
					throw new IllegalArgumentException(
							"kind conflict for variable " + name + ": " + existing + " vs " + kind);
				}
			}
		}
	}
}
