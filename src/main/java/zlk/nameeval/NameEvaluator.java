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

	Env env = new Env();

	public NameEvaluator() {
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

		IdInfo funId = env.get(decl.name());
		IcVar icFun = new IcVar(decl.name(), funId);

		List<IcVar> icArgs = new ArrayList<>();
		List<String> args = decl.args();
		for(int i = 0; i < args.size(); i++) {
			String arg = args.get(i);
			IdInfo argInfo = env.registerArg(arg, getArgType(decl.type(), i), (InfoFun) funId.info(), i);
			icArgs.add(new IcVar(arg, argInfo));
		}

		IcExp icBody = eval(decl.body(), env);

		env.pop();
		return new IcDecl(icFun, icArgs, decl.type(), icBody);
	}

	public IcExp eval(Exp exp, Env env) {
		return exp.fold(
				cnst  -> new IcConst(cnst),
				id    -> new IcVar(id.name(), env.get(id.name())),
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
