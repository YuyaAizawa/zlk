package zlk.nameeval;

import java.util.ArrayList;
import java.util.List;

import zlk.ast.Decl;
import zlk.ast.Exp;
import zlk.ast.Module;
import zlk.common.Type;
import zlk.core.Builtin;
import zlk.idcalc.IcApp;
import zlk.idcalc.IcConst;
import zlk.idcalc.IcDecl;
import zlk.idcalc.IcExp;
import zlk.idcalc.IcIf;
import zlk.idcalc.IcModule;
import zlk.idcalc.IcVar;
import zlk.idcalc.IdInfo;
import zlk.idcalc.InfoFun;

public final class NameEvaluator {

	private final Env env;

	public NameEvaluator(IdGenerator generator) {
		env = new Env(generator);
		Builtin.builtins().forEach(
				fun -> env.registerBuiltinVar(fun.name(), fun.type(), fun.action()));
	}

	public IcModule eval(Module module) {
		env.push();

		// resister toplevels
		module.decls().forEach(decl -> {
			env.registerFun(module.name(), decl.name(), decl.type());
		});

		List<IcDecl> icDecls = module.decls()
				.stream()
				.map(decl -> eval(decl, env))
				.toList();

		env.pop();
		return new IcModule(module.name(), icDecls, module.origin());
	}

	public IcDecl eval(Decl decl, Env env) {
		env.push();

		IdInfo idFun = env.get(decl.name());

		List<IdInfo> idArgs = new ArrayList<>();
		List<String> args = decl.args();
		for(int i = 0; i < args.size(); i++) {
			String arg = args.get(i);
			IdInfo argInfo = env.registerArg((InfoFun) idFun.info(), i, arg, getArgType(decl.type(), i));
			idArgs.add(argInfo);
		}

		IcExp icBody = eval(decl.body(), env);

		env.pop();
		return new IcDecl(idFun, idArgs, idFun.type(), icBody);
	}

	public IcExp eval(Exp exp, Env env) {
		return exp.fold(
				cnst  -> new IcConst(cnst),
				id    -> new IcVar(env.get(id.name())),
				app   -> {
					List<Exp> exps = app.exps();
					IcExp icFun = eval(exps.get(0), env);
					List<IcExp> icArgs = new ArrayList<>();
					for(int i = 1; i < exps.size(); i++) {
						icArgs.add(eval(exps.get(i), env));
					}
					return new IcApp(icFun, icArgs);
				},
				ifExp -> new IcIf(
						eval(ifExp.cond(), env),
						eval(ifExp.exp1(), env),
						eval(ifExp.exp2(), env)));
	}

	private Type getArgType(Type funType, int index) {
		return funType.map(
				unit -> { throw new IllegalArgumentException(); },
				bool -> { throw new IllegalArgumentException(); },
				i32  -> { throw new IllegalArgumentException(); },
				fun  -> {
					if(index == 0) {
						return fun.arg();
					} else {
						return getArgType(fun.ret(), index - 1);
					}
				});
	}
}
