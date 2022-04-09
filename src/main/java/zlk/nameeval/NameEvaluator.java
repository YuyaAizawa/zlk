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
import zlk.idcalc.IcLet;
import zlk.idcalc.IcModule;
import zlk.idcalc.IcVar;
import zlk.idcalc.IdInfo;

public final class NameEvaluator {

	private final Env env;

	public NameEvaluator(IdGenerator generator) {
		env = new Env(generator);
	}

	public IdInfo registerBuiltin(Builtin builtin) {
		return env.registerBuiltinVar(builtin.name(), builtin.type(), builtin.action());
	}

	public IcModule eval(Module module) {
		env.push();

		// resister toplevels
		module.decls().forEach(decl -> {
			env.registerVar(decl.name(), decl.type());
		});

		List<IcDecl> icDecls = module.decls()
				.stream()
				.map(decl -> eval(decl, env))
				.toList();

		env.pop();
		return new IcModule(module.name(), icDecls, module.origin());
	}

	public IcDecl eval(Decl decl, Env env) {
		IdInfo id = env.get(decl.name());

		env.push();

		List<IdInfo> idArgs = new ArrayList<>();
		List<String> args = decl.args();
		for(int i = 0; i < args.size(); i++) {
			String arg = args.get(i);
			IdInfo argInfo = env.registerArg(id, i, arg, getArgType(decl.type(), i));
			idArgs.add(argInfo);
		}

		IcExp icBody = eval(decl.body(), env);

		env.pop();
		return new IcDecl(id, idArgs, id.type(), icBody);
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
						eval(ifExp.exp2(), env)),
				let -> {
					env.push();
					env.registerVar(let.decl().name(), let.decl().type());
					IcDecl decl = eval(let.decl(), env);
					IcExp body = eval(let.body(), env);
					env.pop();

					return new IcLet(decl, body);
				});
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
