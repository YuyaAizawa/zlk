package zlk.nameeval;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import zlk.ast.AType;
import zlk.ast.CaseBranch;
import zlk.ast.Constructor;
import zlk.ast.Decl.ValDecl;
import zlk.ast.Decl.TypeDecl;
import zlk.ast.Exp;
import zlk.ast.Exp.App;
import zlk.ast.Exp.Case;
import zlk.ast.Exp.Cnst;
import zlk.ast.Exp.If;
import zlk.ast.Exp.Let;
import zlk.ast.Module;
import zlk.ast.Pattern;
import zlk.common.ConstValue;
import zlk.common.Type;
import zlk.common.id.Id;
import zlk.common.id.IdMap;
import zlk.core.Builtin;
import zlk.idcalc.IcCaseBranch;
import zlk.idcalc.IcCtor;
import zlk.idcalc.IcFunDecl;
import zlk.idcalc.IcExp;
import zlk.idcalc.IcExp.IcApp;
import zlk.idcalc.IcExp.IcCase;
import zlk.idcalc.IcExp.IcCnst;
import zlk.idcalc.IcExp.IcIf;
import zlk.idcalc.IcExp.IcLet;
import zlk.idcalc.IcExp.IcLetrec;
import zlk.idcalc.IcExp.IcVarCtor;
import zlk.idcalc.IcExp.IcVarForeign;
import zlk.idcalc.IcExp.IcVarLocal;
import zlk.idcalc.IcModule;
import zlk.idcalc.IcPattern;
import zlk.idcalc.IcTypeDecl;
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
		tyEnv.put("Bool", Type.BOOL);
		tyEnv.put("I32" , Type.I32);
	}

	public IcModule eval() {
		env.push(module.name());

		// resister types
		module.decls().forEach(def -> {
			switch(def) {
			case TypeDecl(String name, _, _) -> {
				Type type = new Type.Atom(env.registerVar(name));
				tyEnv.put(name, type);
			}
			default -> {}
			}
		});

		// resister toplevels
		module.decls().forEach(def -> {
			switch(def) {
			case TypeDecl(String name, List<Constructor> ctors, _) -> {
				ctors.forEach(ctor -> registerCtor(ctor, tyEnv.get(name)));
			}
			case ValDecl(String name, _, _, _, _) -> {
				env.registerVar(name);
			}
			}
		});

		List<IcTypeDecl> icTypes = new ArrayList<>();
		List<IcFunDecl> icDecls = new ArrayList<>();

		module.decls().forEach(def -> {
			switch(def) {
			case TypeDecl ty -> icTypes.add(eval(ty));
			case ValDecl fun -> icDecls.add(eval(fun));
			}
		});

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

	public IcTypeDecl eval(TypeDecl union) {
		Id id = env.get(union.name());

		List<IcCtor> ctors = union.ctors().stream().map(ctor -> {
			Id ctorId = env.get(ctor.name());
			List<Type> args = ctor.args().stream().map(ty -> eval(ty)).toList();
			return new IcCtor(ctorId, args, ctor.loc());
		}).toList();

		return new IcTypeDecl(id, ctors, union.loc());
	}

	public IcFunDecl eval(ValDecl decl) {
		try {
			String declName = decl.name();
			env.push(declName);

			Id id = env.get(declName);
			Optional<Type> anno = decl.anno().map(a -> eval(a));
			List<IcPattern> args = decl.args().stream()
					.map(arg -> {
						Id id_ = env.registerVar(arg.name());
						return (IcPattern) new IcPattern.Var(id_, arg.loc());
					})
					.toList();
			IcExp body = eval(decl.body());

			env.pop();

			return new IcFunDecl(
					id,
					anno,
					args,
					body.fv(List.of())
						.contains(id) ?
								Optional.of(List.of()) : // TODO 再帰関数の強連結成分を求めるコンパイルフェーズを作る
									Optional.empty(),
					body,
					decl.loc());
		} catch(RuntimeException e) {
			throw new RuntimeException("in "+decl.name(), e);
		}
	}

	public IcExp eval(Exp exp) {
		switch(exp) {
		case Cnst(ConstValue value, Location loc): {
			return new IcCnst(value, loc);
		}
		case Exp.Var(String name, Location loc): {
			Id id = env.get(name);
			Type ctor = ctorTy.getOrNull(id);
			Type builtin = builtinTy.getOrNull(id);
			if(ctor != null) {
				return new IcVarCtor(id, ctor, loc);
			}
			if(builtin == null) {
				return new IcVarLocal(id, loc);
			} else {
				return new IcVarForeign(id, builtin, loc);
			}
		}
		case App(List<Exp> exps, Location loc): {
			IcExp fun = eval(exps.get(0));
			List<IcExp> args = exps.subList(1, exps.size()).stream()
					.map(arg -> eval(arg))
					.toList();
			return new IcApp(fun, args, loc);
		}
		case If(Exp cond, Exp exp1, Exp exp2, Location loc): {
			return new IcIf(eval(cond), eval(exp1), eval(exp2), loc);
		}
		case Let(List<ValDecl> decls, Exp body, _): {
			return eval(decls, body);
		}
		case Case(Exp exp_, List<CaseBranch> branches, Location loc): {
			IcExp target = eval(exp_);
			List<IcCaseBranch> branches_ = new ArrayList<>();
			for (int i = 0; i < branches.size(); i++) {
				branches_.add(eval(branches.get(i), i));
			}
			return new IcCase(target, branches_, loc);
		}
		}
	}

	private IcExp eval(List<ValDecl> decls, Exp body) {
		if(decls.isEmpty()) {
			return eval(body);
		}

		ValDecl decl = decls.get(0);

		Position end = body.loc().end();
		env.registerVar(decl.name());

		// TODO 再帰関数の強連結成分を求めるコンパイルフェーズを作る
		IcLet tmpLet = new IcLet(eval(decl), eval(decls.subList(1, decls.size()), body),
				new Location(module.origin(), decl.loc().start(), end));

		if(tmpLet.decl().body()
				.fv(List.of())
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
		switch(pat) {
		case Pattern.Var(String name, Location loc): {
			Id id = env.registerVar(name);
			return new IcPattern.Var(id, loc);
		}
		case Pattern.Ctor(String name, List<Pattern> args, Location loc): {
			Id ctor = env.get(name);
			List<Type> argTys = ctorTy.get(ctor).flatten();
			List<IcPattern.Arg> args_ = new ArrayList<>();
			for (int i = 0; i < args.size(); i++) {
				args_.add(new IcPattern.Arg(eval(args.get(i)), argTys.get(i)));
			}
			IcVarCtor icVarCtor = new IcVarCtor(ctor, ctorTy.get(ctor), Location.noLocation());  // TODO location
			return new IcPattern.Ctor(icVarCtor, args_, loc);
		}
		}
	}

	private Type eval(AType aTy) {
		return switch (aTy) {
		case AType.Atom(String name, _) -> tyEnv.get(name);
		case AType.Arrow(AType arg, AType ret, _) -> new Type.Arrow(eval(arg), eval(ret));
		};
	}
}
