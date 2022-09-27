package zlk.nameeval;

import java.util.ArrayList;
import java.util.List;

import zlk.ast.Decl;
import zlk.ast.Exp;
import zlk.ast.Module;
import zlk.common.id.Id;
import zlk.common.id.IdList;
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

		IdList idArgs = new IdList();
		List<String> args = decl.args();
		for(String arg : args) {
			Id argId = env.registerVar(arg);
			idArgs.add(argId);
		}

		IcExp icBody = eval(decl.body());

		env.pop();
		return new IcDecl(id, idArgs, decl.type(), icBody, decl.loc());
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
