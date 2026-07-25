package zlk.nameeval;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import zlk.ast.AnType;
import zlk.ast.CaseBranch;
import zlk.ast.Constructor;
import zlk.ast.Decl;
import zlk.ast.Decl.TypeDecl;
import zlk.ast.Decl.TypeAlias;
import zlk.ast.Decl.TypeErr;
import zlk.ast.Decl.ValDecl;
import zlk.ast.Decl.ValErr;
import zlk.ast.Exp;
import zlk.ast.Exp.App;
import zlk.ast.Exp.Case;
import zlk.ast.Exp.Cnst;
import zlk.ast.Exp.Err;
import zlk.ast.Exp.If;
import zlk.ast.Exp.Lamb;
import zlk.ast.Exp.Let;
import zlk.ast.Exp.Var;
import zlk.ast.Module;
import zlk.ast.Pattern;
import zlk.common.ConstValue;
import zlk.common.Location;
import zlk.common.RecordField;
import zlk.common.Type;
import zlk.common.Type.Row;
import zlk.common.Type.RowVar;
import zlk.common.id.Id;
import zlk.common.id.IdMap;
import zlk.core.Builtin;
import zlk.idcalc.IcCaseBranch;
import zlk.idcalc.IcCtor;
import zlk.idcalc.IcExp;
import zlk.idcalc.IcExp.IcApp;
import zlk.idcalc.IcExp.IcCase;
import zlk.idcalc.IcExp.IcCnst;
import zlk.idcalc.IcExp.IcIf;
import zlk.idcalc.IcExp.IcLamb;
import zlk.idcalc.IcExp.IcLet;
import zlk.idcalc.IcExp.IcVarCtor;
import zlk.idcalc.IcExp.IcVarForeign;
import zlk.idcalc.IcExp.IcVarLocal;
import zlk.idcalc.IcModule;
import zlk.idcalc.IcPattern;
import zlk.idcalc.IcPattern.Arg;
import zlk.idcalc.IcTypeDecl;
import zlk.idcalc.IcValDecl;
import zlk.util.collection.Seq;
import zlk.util.collection.SeqBuffer;

public final class NameEvaluator {

	private enum Kind { TYPE, ROW }

	/** mutableなDFS解決cache/state．immutableな宣言はTyEnv.TyEntry.Aliasが所有し，
	 * ここでは持たない．宣言・state・cacheを一体にしない． */
	private static final class AliasState {
		enum Status { UNVISITED, VISITING, RESOLVED }

		Status status = Status.UNVISITED;
		Type template;
		Seq<Kind> paramKinds;
	}

	private final Module module;
	private final Env env;
	private final TyEnv typeEnv;
	private final IdMap<Builtin> builtins;
	private final IdMap<Type> types;
	private final IdMap<Type> ctors;
	private final IdMap<AliasState> aliasStates = new IdMap<>();
	/** phase 1で登録したalias entry列．phase 2のDFS解決をTyEnvから辿るため，
	 * TyEnvへ登録したTyEntry.Aliasそのものを保持する．宣言の別mapではない． */
	private final java.util.List<TyEnv.TyEntry.Alias> aliasEntries = new java.util.ArrayList<>();

	public NameEvaluator(Module module) {
		this.module = module;
		this.builtins = Builtin.functions().fold(IdMap.folder(b -> b.id(), b -> b));
		this.types = new IdMap<>();
		this.ctors = new IdMap<>();

		// TODO: 組込みのBasic.Boolを作るまでの暫定対応
		ctors.put(Id.intern("Basic.True"), Type.BOOL);
		ctors.put(Id.intern("Basic.False"), Type.BOOL);

		env = new Env();
		typeEnv = new TyEnv();
		Type.BUILTIN.forEach(ty -> {
			try {
				typeEnv.registerNominal(ty.id().simpleName(), ty.id(), 0);
				types.put(ty.id(), ty);
			} catch (DuplicatedNameException e) {
				throw new Error("builtin type dupicated", e);
			}
		});
		builtins.forEach((_, b) -> {
			try {
				env.registerGlobal(b.id());
			} catch (DuplicatedNameException e) {
				throw new Error("builtin value dupicated", e);
			}
		});
	}

	public IcModule eval() {
		env.pushScope(module.name());

		// phase 1: 全TypeDecl/TypeAlias名を同一TyEnvへ先行登録
		module.decls().forEach(def -> {
			switch(def) {
			case TypeDecl(String name, Seq<AnType.Var> args, _, _) -> {
				Id id = Id.intern(env.getScopeName(), name);
				try {
					typeEnv.registerNominal(name, id, args.size());
				} catch (DuplicatedNameException e) {
					throw new RuntimeException(e);
				}
				Seq<Type> tyArgs = args.map(v -> (Type) new Type.Var(v.name()));
				types.put(id, new Type.CtorApp(id, tyArgs));
			}
			case TypeAlias(String name, Seq<AnType.Var> vars, AnType body, _) -> {
				Id id = Id.intern(env.getScopeName(), name);
				// duplicate alias parameterを黙って上書きせず検出する．
				// 宣言順に整列したSeq<Kind>で管理するため，ここで名前重複を弾く．
				java.util.HashSet<String> seen = new java.util.HashSet<>();
				for (var v : vars) {
					if (!seen.add(v.name())) {
						throw new IllegalArgumentException(
								"duplicated type alias parameter: " + v.name()
								+ " in alias " + name);
					}
				}
				TyEnv.AliasDecl decl = new TyEnv.AliasDecl(vars, body);
				try {
					TyEnv.TyEntry.Alias aliasEntry =
							(TyEnv.TyEntry.Alias) typeEnv.registerAlias(name, id, vars.size(), decl);
					aliasStates.put(aliasEntry.id(), new AliasState());
					aliasEntries.add(aliasEntry);
				} catch (DuplicatedNameException e) {
					throw new RuntimeException(e);
				}
			}
			default -> {}
			}
		});

		// phase 2: 全alias bodyをDFS解決．TyEnvのalias entry列から辿る．
		for (TyEnv.TyEntry.Alias alias : aliasEntries) {
			AliasState state = aliasStates.getOrNull(alias.id());
			if (state.status == AliasState.Status.UNVISITED) {
				resolveAlias(alias);
			}
		}

		// phase 3: toplevel registration
		module.decls().forEach(def -> {
			switch(def) {
			case TypeDecl decl -> {
				Id typeId = typeEnv.get(decl.name()).id();
				Type retTy = types.get(typeId);
				decl.ctors().forEach(ctor -> {
					Id ctorId;
					try {
						ctorId = env.register(ctor.name(), Id.intern(typeId, ctor.name()));
					} catch (DuplicatedNameException e) {
						throw new RuntimeException(e);
					}
					Type type = Type.fromSeq(Seq.concat(
							evalConstructorArgs(decl, ctor),
							Seq.of(retTy)));
					this.ctors.put(ctorId, type);
				});
			}
			case ValDecl(String name, _, _, _, _) -> {
				try {
					env.register(name);
				} catch (DuplicatedNameException e) {
					throw new RuntimeException(e);
				}
			}
			case TypeAlias _ -> {}
			case ValErr _, TypeErr _ -> {
				throw new IllegalStateException();
			}
			}
		});

		SeqBuffer<IcTypeDecl> icTypes = new SeqBuffer<>();
		SeqBuffer<IcValDecl> icDecls = new SeqBuffer<>();

		module.decls().forEach(def -> {
			switch(def) {
			case TypeDecl ty -> icTypes.add(eval(ty));
			case ValDecl fun -> icDecls.add(eval(fun));
			case TypeAlias _, ValErr _, TypeErr _ -> {}
			}
		});

		env.popScope();
		if(env.scopes.size() != 0) {
			throw new AssertionError();
		}
		return new IcModule(module.name(), icTypes.toSeq(), icDecls.toSeq());
	}

	/** alias本体をDFS解決し，templateとparamKindsをcacheへ格納する．
	 * 本体の解決中にRuntimeException/Errorが発生した場合は，自分のstateだけを
	 * UNVISITEDへ戻しtemplate/paramKindsをnullにして再throwする．
	 * A→B→Aのcycleでは内側の例外がB，続いて外側Aのcatchがunwindし，各frameが自己復旧する．
	 * 再入時のVISITING検出は従来どおりcycle reject． */
	private void resolveAlias(TyEnv.TyEntry.Alias alias) {
		AliasState state = aliasStates.getOrNull(alias.id());
		if (state == null) return;
		switch (state.status) {
		case RESOLVED -> { return; }
		case VISITING -> { throw new IllegalStateException("recursive type alias: " + alias.id().simpleName()); }
		case UNVISITED -> {
			TyEnv.AliasDecl decl = alias.decl();
			state.status = AliasState.Status.VISITING;
			try {
				// kind推論中の名前lookup用local mapは許容するが，
				// 確定cacheはMapで保持しない．確定cacheは宣言順Seq<Kind>．
				Map<String, Kind> localKinds = new HashMap<>();
				for (var v : decl.params()) {
					localKinds.put(v.name(), null);
				}
				Type template = evalAsType(decl.body(), localKinds, true);
				// 未使用paramはTYPE既定．宣言順に整列したSeq<Kind>として保存する．
				SeqBuffer<Kind> kinds = new SeqBuffer<>();
				for (var v : decl.params()) {
					Kind k = localKinds.get(v.name());
					kinds.add(k != null ? k : Kind.TYPE);
				}
				state.template = template;
				state.paramKinds = kinds.toSeq();
				state.status = AliasState.Status.RESOLVED;
			} catch (RuntimeException | Error e) {
				// 自己復旧: 自分のstateだけをUNVISITEDへ戻し，cacheをクリアして再throwする．
				// 外側frameのVISITINGは外側frame自身のcatchで復旧される．
				state.status = AliasState.Status.UNVISITED;
				state.template = null;
				state.paramKinds = null;
				throw e;
			}
		}
		}
	}

	private Type applyAlias(TyEnv.TyEntry.Alias alias, Seq<AnType> args, Map<String, Kind> kindCtx, boolean isAliasBody) {
		resolveAlias(alias);
		TyEnv.AliasDecl decl = alias.decl();
		AliasState state = aliasStates.getOrNull(alias.id());

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
				Row row = evalAsRow(arg, kindCtx, isAliasBody);
				rowBinds.put(paramName, row);
			} else {
				Type t = evalAsType(arg, kindCtx, isAliasBody);
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

	private void registerKind(Map<String, Kind> kindCtx, String name, Kind kind, boolean isAliasBody) {
		if (isAliasBody) {
			if (!kindCtx.containsKey(name)) {
				throw new IllegalArgumentException("undeclared type variable in alias body: " + name);
			}
			Kind existing = kindCtx.get(name);
			if (existing != null && existing != kind) {
				throw new IllegalArgumentException(
					"kind conflict for variable " + name + ": " + existing + " vs " + kind);
			}
			kindCtx.put(name, kind);
		} else {
			Kind existing = kindCtx.get(name);
			if (existing != null && existing != kind) {
				throw new IllegalArgumentException(
					"kind conflict for variable " + name + ": " + existing + " vs " + kind);
			}
			kindCtx.put(name, kind);
		}
	}

	/** value annotation / ADT constructor引数の型評価エントリ */
	private Type evalAnnoType(AnType aTy) {
		Map<String, Kind> kindCtx = new HashMap<>();
		return evalAsType(aTy, kindCtx, false);
	}

	/** AnTypeをTYPE文脈で評価してsemantic Typeを返す */
	private Type evalAsType(AnType aTy, Map<String, Kind> kindCtx, boolean isAliasBody) {
		return switch (aTy) {
		case AnType.Unit _ -> Type.UNIT;
		case AnType.Var(String name, _) -> {
			registerKind(kindCtx, name, Kind.TYPE, isAliasBody);
			yield new Type.Var(name);
		}
		case AnType.Type(String ctor, Seq<AnType> args, _) -> {
			TyEnv.TyEntry entry = typeEnv.getOrNull(ctor);
			if (entry == null) {
				throw new IllegalArgumentException("unknown type: " + ctor);
			}
			if (entry.arity() != args.size()) {
				throw new IllegalArgumentException(
					"arity mismatch for " + ctor
					+ ": expected " + entry.arity() + ", got " + args.size());
			}
			if (entry instanceof TyEnv.TyEntry.Alias aliasEntry) {
				yield applyAlias(aliasEntry, args, kindCtx, isAliasBody);
			} else {
				Seq<Type> argTypes = args.map(a -> evalAsType(a, kindCtx, isAliasBody));
				yield new Type.CtorApp(entry.id(), argTypes);
			}
		}
		case AnType.Arrow(AnType arg, AnType ret, _) ->
			new Type.Arrow(evalAsType(arg, kindCtx, isAliasBody), evalAsType(ret, kindCtx, isAliasBody));
		case AnType.Record(Optional<AnType.Var> extension, Seq<AnType.RecordField> fields, _) -> {
			Seq<RecordField<Type>> fieldTypes = fields.map(f ->
				new RecordField<>(f.name(), evalAsType(f.type(), kindCtx, isAliasBody)));
			Optional<RowVar> ext = extension.map(v -> {
				registerKind(kindCtx, v.name(), Kind.ROW, isAliasBody);
				return new RowVar(v.name());
			});
			yield new Type.Record(new Row(fieldTypes, ext));
		}
		};
	}

	/** AnTypeをROW文脈で評価してRowを返す */
	private Row evalAsRow(AnType aTy, Map<String, Kind> kindCtx, boolean isAliasBody) {
		switch (aTy) {
		case AnType.Record(Optional<AnType.Var> ext, Seq<AnType.RecordField> fields, _):
			Seq<RecordField<Type>> fieldTypes = fields.map(f ->
				new RecordField<>(f.name(), evalAsType(f.type(), kindCtx, isAliasBody)));
			Optional<RowVar> ext2 = ext.map(v -> {
				registerKind(kindCtx, v.name(), Kind.ROW, isAliasBody);
				return new RowVar(v.name());
			});
			return new Row(fieldTypes, ext2);
		case AnType.Var(String name, _):
			// Foo aでROW期待位置のAnType.Var aはRecord([], RowVar(a))相当
			registerKind(kindCtx, name, Kind.ROW, isAliasBody);
			return new Row(Seq.of(), Optional.of(new RowVar(name)));
		default:
			Type t = evalAsType(aTy, kindCtx, isAliasBody);
			if (t instanceof Type.Record rec) {
				return rec.row();
			}
			throw new IllegalArgumentException(
				"ROW argument must be a record, but got: " + t);
		}
	}

	private Seq<Type> evalConstructorArgs(TypeDecl decl, Constructor ctor) {
		Map<String, Kind> kindCtx = new HashMap<>();
		for(AnType.Var var : decl.vars()) {
			if(kindCtx.putIfAbsent(var.name(), Kind.TYPE) != null) {
				throw new IllegalArgumentException(
						"duplicated type parameter: " + var.name());
			}
		}
		return ctor.args().map(ty -> evalAsType(ty, kindCtx, false));
	}

	public IcTypeDecl eval(TypeDecl union) {
		Id id = typeEnv.get(union.name()).id();

		Seq<Type> vars = union.vars().map(var -> (Type) new Type.Var(var.name()));

		Seq<IcCtor> ctors = union.ctors().map(ctor -> {
			Id ctorId = env.get(ctor.name());
			Seq<Type> args = evalConstructorArgs(union, ctor);
			return new IcCtor(ctorId, args, ctor.loc());
		});

		return new IcTypeDecl(id, vars, ctors, union.loc());
	}

	public IcValDecl eval(ValDecl decl) {
		try {
			String declName = decl.name();
			env.pushScope(declName);

			Id id = env.get(declName);
			Optional<Type> anno = decl.anno().map(a -> evalAnnoType(a));
			Seq<IcPattern> args = decl.args().map(a -> eval(a));
			IcExp body = eval(decl.body(), id);

			env.popScope();

			return new IcValDecl(id, anno, args, body, decl.loc());
		} catch(RuntimeException e) {
			throw new RuntimeException("in "+decl.name(), e);
		}
	}

	private IcExp eval(Exp exp, Id scope) {
		return switch(exp) {
		case Cnst(ConstValue value, Location loc) ->
			new IcCnst(value, loc);

		case Var(String name, Location loc) -> {
			Id id = env.get(name);

			Builtin builtin = builtins.getOrNull(id);
			if(builtin != null) {
				yield new IcVarForeign(id, builtin.type(), loc);
			}

			Type ctor = ctors.getOrNull(id);
			if(ctor != null) {
				yield new IcVarCtor(id, ctor, loc);
			}

			yield new IcVarLocal(id, loc);
		}

		case Lamb(Seq<Pattern> patterns, Exp body, Location loc) -> {
			env.pushScope();
			IcExp result = new IcLamb(
					env.getScopeName(),
					patterns.map(a -> eval(a)),
					eval(body, scope),
					loc);
			env.popScope();
			yield result;
		}

		case App(Seq<Exp> exps, Location loc) ->
			new IcApp(
					eval(exps.head(), scope),
					exps.tail().map(arg -> eval(arg, scope)),
					loc);

		case If(Exp cond, Exp exp1, Exp exp2, Location loc) ->
			new IcIf(
					eval(cond, scope),
					eval(exp1, scope),
					eval(exp2, scope),
					loc);

		case Let(Seq<Decl.Value> decls, Exp body, Location loc) -> {
			if(decls.isEmpty()) {
				yield eval(body, scope);
			} else {
				Seq<ValDecl> validDecls = decls.map(decl -> switch(decl) {
				case ValDecl valDecl -> valDecl;
				case ValErr _ -> throw new IllegalArgumentException();
				});
				for(ValDecl decl: validDecls) {
					try {
						env.register(decl.name());
					} catch (DuplicatedNameException e) {
						throw new RuntimeException(e);
					}
				}
				yield new IcLet(
						validDecls.map(decl -> eval(decl)),
						eval(body, scope),
						loc);
			}
		}

		case Case(Exp exp_, Seq<CaseBranch> branches, Location loc) ->
			new IcCase(
					eval(exp_, scope),
					branches.mapIndexed((i, branch) -> eval(branch, i, scope)),
					loc);

		case Exp.Record(Seq<Exp.RecordField> fields, Location loc) ->
			new IcExp.IcRecord(
					fields.map(field -> new IcExp.IcRecordField(
							field.name(),
							eval(field.value(), scope),
							field.loc())),
					loc);

		case Exp.RecordAccess(Exp target, String field, Location loc) ->
			new IcExp.IcRecordAccess(eval(target, scope), field, loc);

		case Exp.RecordUpdate(Exp target, Seq<Exp.RecordField> fields, Location loc) ->
			new IcExp.IcRecordUpdate(
					eval(target, scope),
					fields.map(field -> new IcExp.IcRecordField(
							field.name(), eval(field.value(), scope), field.loc())),
					loc);

		case Err _ ->
			throw new IllegalArgumentException();
		};
	}

	private IcCaseBranch eval(CaseBranch branch, int branchIdx, Id scope) {
		env.pushScope("_" + branchIdx);
		IcPattern pat = eval(branch.pattern());
		IcExp body = eval(branch.body(), scope);
		env.popScope();
		return new IcCaseBranch(pat, body, branch.loc());
	}

	private IcPattern eval(Pattern pat) {
		switch(pat) {
		case Pattern.Wildcard(Location loc): {
			return new IcPattern.Wildcard(loc);
		}
		case Pattern.Var(String name, Location loc): {
			Id id;
			try {
				id = env.register(name);
			} catch (DuplicatedNameException e) {
				throw new RuntimeException(e);
			}
			return new IcPattern.Var(id, loc);
		}
		case Pattern.Ctor(String name, Seq<Pattern> args, Location loc): {
			Id ctor = env.get(name);
			IcVarCtor icVarCtor = new IcVarCtor(ctor, ctors.get(ctor), Location.noLocation());
			Seq<Type> argTys = ctors.get(ctor).flatten();
			Seq<Arg> dectorArgs = args.mapIndexed((i, arg) -> new IcPattern.Arg(eval(arg), argTys.at(i)));
			return new IcPattern.Dector(icVarCtor, dectorArgs, loc);
		}
		case Pattern.Record(Seq<Pattern.RecordField> fields, Location loc): {
			return new IcPattern.Record(
					fields.map(field -> new IcPattern.RecordField(
							field.name(), eval(field.pattern()), field.loc())),
					loc);
		}
		case Pattern.Err _: {
			throw new IllegalArgumentException();
		}
		}
	}
}