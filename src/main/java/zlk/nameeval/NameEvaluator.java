package zlk.nameeval;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import zlk.ast.AType;
import zlk.ast.CaseBranch;
import zlk.ast.Constructor;
import zlk.ast.Decl;
import zlk.ast.Exp;
import zlk.ast.Module;
import zlk.ast.Pattern;
import zlk.ast.Union;
import zlk.common.id.Id;
import zlk.common.id.IdMap;
import zlk.common.type.TyArrow;
import zlk.common.type.TyAtom;
import zlk.common.type.Type;
import zlk.core.Builtin;
import zlk.idcalc.IcApp;
import zlk.idcalc.IcCase;
import zlk.idcalc.IcCaseBranch;
import zlk.idcalc.IcCnst;
import zlk.idcalc.IcCtor;
import zlk.idcalc.IcDecl;
import zlk.idcalc.IcExp;
import zlk.idcalc.IcIf;
import zlk.idcalc.IcLet;
import zlk.idcalc.IcLetrec;
import zlk.idcalc.IcModule;
import zlk.idcalc.IcPCtor;
import zlk.idcalc.IcPCtorArg;
import zlk.idcalc.IcPVar;
import zlk.idcalc.IcPattern;
import zlk.idcalc.IcType;
import zlk.idcalc.IcVarCtor;
import zlk.idcalc.IcVarForeign;
import zlk.idcalc.IcVarLocal;
import zlk.util.Location;
import zlk.util.Position;

public final class NameEvaluator {

	private final Module module;
	private final Env env;
	private final TyEnv tyEnv;
	private final IdMap<Type> ctorTy;
	private final IdMap<Type> builtinTy;

	public NameEvaluator(Module module, List<Builtin> builtinFuncs) {
		this.module = module;
		this.ctorTy = new IdMap<>();
		this.builtinTy = new IdMap<>();

		env = new Env();
		for(Builtin builtin : builtinFuncs) {
			builtinTy.put(env.registerBuiltinVar(builtin.id()), builtin.type());
		}

		tyEnv = new TyEnv();
		tyEnv.put("Bool", TyAtom.BOOL);
		tyEnv.put("I32" , TyAtom.I32);
	}

	public IcModule eval() {
		env.push(module.name());

		// resister types
		module.decls().forEach(def ->
			def.match(
					union -> {
						Type type = new TyAtom(env.registerVar(union.name()));
						tyEnv.put(union.name(), type);
					},
					decl -> {}));

		// resister toplevels
		module.decls().forEach(def ->
			def.match(
					union -> union.ctors().forEach(
							ctor -> registerCtor(ctor, tyEnv.get(union.name()))),
					decl -> env.registerVar(decl.name())));

		List<IcType> icTypes = new ArrayList<>();
		List<IcDecl> icDecls = new ArrayList<>();

		module.decls().forEach(def ->
				def.fold(
						union -> icTypes.add(eval(union)),
						decl  -> icDecls.add(eval(decl))));

		env.pop();
		if(env.scopeStack.size() != 1) {
			throw new AssertionError();
		}
		return new IcModule(module.name(), icTypes, icDecls, module.origin());
	}

	private void registerCtor(Constructor ctor, Type type) {
		Id id = env.registerVar(ctor.name());
		Type type_ = Type.arrow(ctor.args().stream().map(ty -> eval(ty)).toList(), type);
		ctorTy.put(id, type_);
	}

	public IcType eval(Union union) {
		Id id = env.get(union.name());

		List<IcCtor> ctors = union.ctors().stream().map(ctor -> {
			Id ctorId = env.get(ctor.name());
			List<Type> args = ctor.args().stream().map(ty -> eval(ty)).toList();
			return new IcCtor(ctorId, args, ctor.loc());
		}).toList();

		return new IcType(id, ctors, union.loc());
	}

	public IcDecl eval(Decl decl) {
		try {
			String declName = decl.name();
			env.push(declName);

			Id id = env.get(declName);
			Optional<Type> anno = decl.anno().map(a -> eval(a));
			List<IcPattern> args = decl.args().stream()
					.map(arg -> {
						Id id_ = env.registerVar(arg.name());
						return (IcPattern) new IcPVar(id_, arg.loc());
					})
					.toList();
			IcExp body = eval(decl.body());

			env.pop();

			// TODO 再帰関数の強連結成分を求めるコンパイルフェーズを作る
			return new IcDecl(
					id,
					anno,
					args,
					body.fv(List.of())
						.stream()
						.map(var -> var.id())
						.toList()
						.contains(id) ?
								Optional.of(List.of()) :
									Optional.empty(),
					body,
					decl.loc());
		} catch(RuntimeException e) {
			throw new RuntimeException("in "+decl.name(), e);
		}
	}

	public IcExp eval(Exp exp) {
		return exp.fold(
				cnst  -> new IcCnst(cnst.value(), cnst.loc()),
				var   -> {
					Id id = env.get(var.name());
					Type ctor = ctorTy.getOrNull(id);
					Type builtin = builtinTy.getOrNull(id);
					if(ctor != null) {
						return new IcVarCtor(id, ctor, var.loc());
					}
					if(builtin == null) {
						return new IcVarLocal(id, var.loc());
					} else {
						return new IcVarForeign(id, builtin, var.loc());
					}
				},
				app   -> {
					List<Exp> exps = app.exps();

					IcExp fun = eval(exps.get(0));
					List<IcExp> args = exps.subList(1, exps.size()).stream()
							.map(arg -> eval(arg))
							.toList();
					return new IcApp(fun, args, app.loc());
				},
				ifExp -> new IcIf(
						eval(ifExp.cond()),
						eval(ifExp.exp1()),
						eval(ifExp.exp2()),
						ifExp.loc()),
				let -> {
					return eval(let.decls(), let.body());
				},
				case_ -> {
					IcExp target = eval(case_.exp());
					List<IcCaseBranch> branches = new ArrayList<>();
					for (int i = 0; i < case_.branches().size(); i++) {
						branches.add(eval(case_.branches().get(i), i));
					}
					return new IcCase(target, branches, case_.loc());
				});
	}

	private IcExp eval(List<Decl> decls, Exp body) {
		if(decls.isEmpty()) {
			return eval(body);
		}

		Decl decl = decls.get(0);

		Position end = body.loc().end();
		env.registerVar(decl.name());

		// TODO 再帰関数の強連結成分を求めるコンパイルフェーズを作る
		IcLet tmpLet = new IcLet(eval(decl), eval(decls.subList(1, decls.size()), body),
				new Location(module.origin(), decl.loc().start(), end));

		if(tmpLet.decl().body().fv(List.of()).stream()
				.map(var -> var.id())
				.toList()
				.contains(tmpLet.decl().id())) {
			return new IcLetrec(List.of(tmpLet.decl().norec()), tmpLet.body(), tmpLet.loc());
		} else {
			return tmpLet;
		}
	}

	private IcCaseBranch eval(CaseBranch branch, int branchIdx) {
		env.push("_" + branchIdx);
		IcPattern pat = eval(branch.pattern());
		IcExp body = eval(branch.body());
		env.pop();
		return new IcCaseBranch(pat, body, branch.loc());
	}

	private IcPattern eval(Pattern pat) {
		return pat.fold(
				var -> {
					Id id = env.registerVar(var.name());
					return new IcPVar(id, var.loc());
				},
				pctor -> {
					Id ctor = env.get(pctor.name());

					List<Pattern> argPats = pctor.args();
					List<Type> argTys = ctorTy.get(ctor).flatten();
					List<IcPCtorArg> args = new ArrayList<>();
					for (int i = 0; i < argPats.size(); i++) {
						args.add(new IcPCtorArg(eval(argPats.get(i)), argTys.get(i)));
					}

					return new IcPCtor(
							new IcVarCtor(ctor, null, Location.noLocation()), // TODO location
							args,
							pctor.loc());
				});
	}

	private Type eval(AType aTy) {
		return aTy.fold(
				base -> tyEnv.get(base.name()),
				arrow -> new TyArrow(eval(arrow.arg()), eval(arrow.ret())));
	}
}
