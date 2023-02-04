package zlk.nameeval;

import java.util.ArrayList;
import java.util.List;

import zlk.ast.Decl;
import zlk.ast.Exp;
import zlk.ast.Module;
import zlk.common.id.Id;
import zlk.common.id.IdList;
import zlk.common.type.TyArrow;
import zlk.common.type.Type;
import zlk.idcalc.IcAbs;
import zlk.idcalc.IcApp;
import zlk.idcalc.IcCnst;
import zlk.idcalc.IcDecl;
import zlk.idcalc.IcExp;
import zlk.idcalc.IcIf;
import zlk.idcalc.IcLet;
import zlk.idcalc.IcModule;
import zlk.idcalc.IcVar;
import zlk.util.Location;
import zlk.util.Position;

public final class NameEvaluator {

	private final Module module;
	private final Env env;

	public NameEvaluator(Module module, IdList builtins) {
		this.module = module;
		env = new Env();
		for(Id builtin : builtins) {
			env.registerBuiltinVar(builtin);
		}
	}

	public IcModule eval() {
		env.push(module.name());

		// resister toplevels
		module.decls().forEach(decl -> {
			env.registerVar(decl.name());
		});

		List<IcDecl> icDecls = module.decls()
				.stream()
				.map(decl -> eval(decl))
				.toList();

		env.pop();
		if(env.scopeStack.size() != 1) {
			throw new AssertionError();
		}
		return new IcModule(module.name(), icDecls, module.origin());
	}

	public IcDecl eval(Decl decl) {
		String declName = decl.name();
		env.push(declName);

		Id id = env.get(declName);

		IcExp icBody = argHelp(decl.args(), decl.type(), decl.body(), decl.loc());

		env.pop();
		return new IcDecl(id, decl.type(), icBody, decl.loc());
	}
	private IcExp argHelp(List<String> args, Type type, Exp body, Location loc) {
		if(args.isEmpty()) {
			return eval(body);
		} else {
			TyArrow arrow = type.asArrow();
			return new IcAbs(
					env.registerVar(args.get(0)),
					arrow.arg(),
					argHelp(args.subList(1, args.size()), arrow.ret(), body, loc),
					loc);
		}
	}

	public IcExp eval(Exp exp) {
		return exp.fold(
				cnst  -> new IcCnst(cnst.value(), cnst.loc()),
				var   -> new IcVar(env.get(var.name()), var.loc()),
				app   -> {
					List<Exp> exps = app.exps();
					IcExp icFun = eval(exps.get(0));
					List<IcExp> icArgs = new ArrayList<>(exps.size()-1);
					for(int i = 1; i < exps.size(); i++) {
						icArgs.add(eval(exps.get(i)));
					}
					return new IcApp(icFun, icArgs, app.loc());
				},
				ifExp -> new IcIf(
						eval(ifExp.cond()),
						eval(ifExp.exp1()),
						eval(ifExp.exp2()),
						ifExp.loc()),
				let -> {
					return eval(let.decls(), let.body());
				});
	}

	private IcExp eval(List<Decl> decls, Exp body) {
		if(decls.isEmpty()) {
			return eval(body);
		}

		Decl decl = decls.get(0);

		Position end = body.loc().end();
		env.registerVar(decl.name());

		return new IcLet(eval(decl), eval(decls.subList(1, decls.size()), body),
				new Location(module.origin(), decl.loc().start(), end));
		}
}
