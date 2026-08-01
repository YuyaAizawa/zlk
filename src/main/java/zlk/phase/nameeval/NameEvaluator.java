package zlk.phase.nameeval;

import java.util.Optional;
import java.util.HashMap;
import java.util.Map;

import zlk.common.ConstValue;
import zlk.common.Location;
import zlk.common.Type;
import zlk.common.id.Id;
import zlk.common.id.IdMap;
import zlk.core.Builtin;
import zlk.diagnostic.Diagnostic;
import zlk.diagnostic.DiagnosticReporter;
import zlk.ir.ast.CaseBranch;
import zlk.ir.ast.Decl;
import zlk.ir.ast.Exp;
import zlk.ir.ast.Module;
import zlk.ir.ast.Pattern;
import zlk.ir.ast.Decl.TypeAlias;
import zlk.ir.ast.Decl.TypeDecl;
import zlk.ir.ast.Decl.TypeErr;
import zlk.ir.ast.Decl.ValDecl;
import zlk.ir.ast.Decl.ValErr;
import zlk.ir.ast.Exp.App;
import zlk.ir.ast.Exp.Case;
import zlk.ir.ast.Exp.Cnst;
import zlk.ir.ast.Exp.Err;
import zlk.ir.ast.Exp.If;
import zlk.ir.ast.Exp.Lamb;
import zlk.ir.ast.Exp.Let;
import zlk.ir.ast.Exp.Var;
import zlk.ir.idcalc.IcCaseBranch;
import zlk.ir.idcalc.IcCtor;
import zlk.ir.idcalc.IcExp;
import zlk.ir.idcalc.IcModule;
import zlk.ir.idcalc.IcPattern;
import zlk.ir.idcalc.IcTypeDecl;
import zlk.ir.idcalc.IcValDecl;
import zlk.phase.PhaseResult;
import zlk.ir.idcalc.IcExp.IcApp;
import zlk.ir.idcalc.IcExp.IcCase;
import zlk.ir.idcalc.IcExp.IcCnst;
import zlk.ir.idcalc.IcExp.IcIf;
import zlk.ir.idcalc.IcExp.IcLamb;
import zlk.ir.idcalc.IcExp.IcLet;
import zlk.ir.idcalc.IcExp.IcVarCtor;
import zlk.ir.idcalc.IcExp.IcVarForeign;
import zlk.ir.idcalc.IcExp.IcVarLocal;
import zlk.ir.idcalc.IcPattern.Arg;
import zlk.util.collection.Seq;
import zlk.util.collection.SeqBuffer;

/**
 * 名前評価器．値名前解決，AST expression／patternのidcalc化，module phase orchestrationを担う．
 *
 * <p>alias/kind-aware型解決は {@link TypeResolver} へ分離しており，本クラスは
 * 次のphase順序だけを統括する．
 * <ol>
 *   <li>module value scope進入</li>
 *   <li>全TypeDecl／TypeAliasの事前解決（TypeResolver API）</li>
 *   <li>constructor／toplevel value登録</li>
 *   <li>IcTypeDecl／IcValDecl生成</li>
 * </ol>
 * 入力ASTと出力IcModuleの意味論は不変．
 */
public final class NameEvaluator {

	private final Module module;
	private final Env env;
	private final TypeResolver typeResolver;
	private final IdMap<Builtin> builtins;
	/** 暫定的な組込みconstructor signature表（Id → Type）． */
	private final IdMap<Type> builtinCtors;
	private final Map<Id, Location> nameLocations = new HashMap<>();

	public NameEvaluator(Module module) {
		this.module = module;
		this.builtins = Builtin.functions().fold(IdMap.folder(b -> b.id(), b -> b));
		this.builtinCtors = new IdMap<>();

		// TODO: 組込みのBasic.Boolを作るまでの暫定対応
		builtinCtors.put(Id.intern("Basic.True"), Type.BOOL);
		builtinCtors.put(Id.intern("Basic.False"), Type.BOOL);

		env = new Env();
		typeResolver = new TypeResolver();
		builtins.forEach((_, b) -> {
			try {
				env.registerGlobal(b.id());
			} catch (DuplicatedNameException e) {
				throw new Error("builtin value dupicated", e);
			}
		});
	}

	public IcModule eval() {
		return env.withScope(module.name(), moduleOwner -> {
			// 型名の登録，parameter kindの解決，aliasの解決を行う
			typeResolver.resolveDeclarations(module.decls(), moduleOwner);

			// toplevelのvalueとconstructorを登録する
			module.decls().forEach(def -> {
				switch(def) {
				case TypeDecl decl -> {
					decl.ctors().forEach(ctor -> {
						try {
							Id ctorId = env.register(ctor.name(), Id.intern(typeResolver.getTypeId(decl.name()), ctor.name()));
							nameLocations.put(ctorId, ctor.loc());
							typeResolver.registerConstructor(decl, ctor, ctorId);
						} catch (DuplicatedNameException e) {
							throw duplicateName(ctor.loc(), Diagnostic.NameNamespace.CONSTRUCTOR, ctor.name(), e);
						}
					});
				}
				case ValDecl(String name, _, _, _, _) -> {
					try {
						Id id = env.register(name);
						nameLocations.put(id, def.loc());
					} catch (DuplicatedNameException e) {
						throw duplicateName(def.loc(), Diagnostic.NameNamespace.VALUE, name, e);
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

			return new IcModule(module.name(), icTypes.toSeq(), icDecls.toSeq());
		});
	}

	/** Source-facing resolution failures are reported and stop this phase. */
	public PhaseResult<IcModule> eval(DiagnosticReporter reporter) {
		try {
			return PhaseResult.ready(eval());
		} catch (ResolutionException error) {
			reporter.report(error.diagnostic());
			return PhaseResult.blocked();
		}
	}

	public IcTypeDecl eval(TypeDecl union) {
		Id id = typeResolver.getTypeId(union.name());

		Seq<Type> vars = typeResolver.getTypeParameters(id);

		Seq<IcCtor> ctors = union.ctors().map(ctor -> {
			Id ctorId = env.get(ctor.name());
			Seq<Type> args = typeResolver.getConstructorArgs(ctorId);
			return new IcCtor(ctorId, args, ctor.loc());
		});

		return new IcTypeDecl(id, vars, ctors, union.loc());
	}

	public IcValDecl eval(ValDecl decl) {
		try {
			return env.withScope(decl.name(), _ -> {
				String declName = decl.name();
				Id id = env.get(declName);
				Optional<Type> anno = decl.anno().map(a -> typeResolver.evalAnnotation(a));
				Seq<IcPattern> args = decl.args().map(a -> eval(a));
				IcExp body = eval(decl.body(), id);

				return new IcValDecl(id, anno, args, body, decl.loc());
			});
		} catch (ResolutionException error) {
			throw error;
		} catch (RuntimeException e) {
			throw new RuntimeException("in "+decl.name(), e);
		}
	}

	private IcExp eval(Exp exp, Id scope) {
		return switch(exp) {
		case Cnst(ConstValue value, Location loc) ->
			new IcCnst(value, loc);

		case Var(String name, Location loc) -> {
			Id id = getName(name, loc);

			Builtin builtin = builtins.getOrNull(id);
			if(builtin != null) {
				yield new IcVarForeign(id, builtin.type(), loc);
			}

			Type ctor = getConstructorTypeOrNull(id);
			if(ctor != null) {
				yield new IcVarCtor(id, ctor, loc);
			}

			yield new IcVarLocal(id, loc);
		}

		case Lamb(Seq<Pattern> patterns, Exp body, Location loc) -> {
			yield env.withLambdaScope(lambdaOwner ->
					new IcLamb(
						lambdaOwner,
						patterns.map(a -> eval(a)),
						eval(body, scope),
						loc));
		}

		case App(Seq<Exp> exps, Location loc) -> {
			IcExp fun = eval(exps.head(), scope);
			Seq<IcExp> args = exps.tail().map(arg -> eval(arg, scope));
			if (fun instanceof IcVarCtor ctor) {
				int expected = ctor.type().flatten().size() - 1;
				if (args.size() > expected) {
					throw new ResolutionException(new Diagnostic.ConstructorArityMismatch(
							loc, ctor.id().simpleName(), expected, args.size()));
				}
			}
			yield new IcApp(fun, args, loc);
		}

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
				// let は親と同じ owner を共有する一時 binding frame を持ち，
				// 宣言群と body をその frame 内で評価し，退出後に binding を破棄する．
				yield env.withLetFrame(() -> {
					for(ValDecl decl: validDecls) {
						try {
							Id id = env.register(decl.name());
							nameLocations.put(id, decl.loc());
						} catch (DuplicatedNameException e) {
							throw duplicateName(decl.loc(), Diagnostic.NameNamespace.VALUE, decl.name(), e);
						}
					}
					return new IcLet(
							validDecls.map(decl -> eval(decl)),
							eval(body, scope),
							loc);
				});
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
		return env.withScope("_" + branchIdx, _ -> {
			IcPattern pat = eval(branch.pattern());
			IcExp body = eval(branch.body(), scope);
			return new IcCaseBranch(pat, body, branch.loc());
		});
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
				throw duplicateName(loc, Diagnostic.NameNamespace.VALUE, name, e);
			}
			nameLocations.put(id, loc);
			return new IcPattern.Var(id, loc);
		}
		case Pattern.Ctor(String name, Seq<Pattern> args, Location loc): {
			Id ctor = getName(name, loc);
			Type ctorType = getConstructorType(ctor);
			IcVarCtor icVarCtor = new IcVarCtor(ctor, ctorType, Location.noLocation());
			int expected = ctorType.flatten().size() - 1;
			if (args.size() != expected) {
				throw new ResolutionException(new Diagnostic.ConstructorArityMismatch(loc, name, expected, args.size()));
			}
			Seq<Arg> dectorArgs = Seq.zip(args, ctorType.flatten().take(args.size()))
					.map((arg, argTy) -> new IcPattern.Arg(eval(arg), argTy));
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

	private Type getConstructorTypeOrNull(Id id) {
		Type builtin = builtinCtors.getOrNull(id);
		return builtin != null ? builtin : typeResolver.getConstructorTypeOrNull(id);
	}

	private Type getConstructorType(Id id) {
		Type type = getConstructorTypeOrNull(id);
		if (type == null) {
			throw new IllegalArgumentException("not a constructor: " + id);
		}
		return type;
	}

	private Id getName(String name, Location loc) {
		Id id = env.getOrNull(name);
		if (id == null) {
			throw new ResolutionException(new Diagnostic.UnknownName(loc, name));
		}
		return id;
	}

	private ResolutionException duplicateName(
			Location location, Diagnostic.NameNamespace namespace, String name, DuplicatedNameException error
	) {
		return new ResolutionException(new Diagnostic.DuplicateName(
				location, namespace, name, nameLocations.get(error.oldId)));
	}
}
