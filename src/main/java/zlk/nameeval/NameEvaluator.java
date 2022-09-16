package zlk.nameeval;

import java.util.ArrayList;
import java.util.List;

import zlk.ast.Decl;
import zlk.ast.Exp;
import zlk.ast.Module;
import zlk.common.id.Id;
import zlk.common.id.IdGenerator;
import zlk.common.id.IdList;
import zlk.core.Builtin;
import zlk.idcalc.IcApp;
import zlk.idcalc.IcConst;
import zlk.idcalc.IcDecl;
import zlk.idcalc.IcExp;
import zlk.idcalc.IcIf;
import zlk.idcalc.IcLet;
import zlk.idcalc.IcModule;
import zlk.idcalc.IcVar;
import zlk.util.Location;
import zlk.util.Position;

public final class NameEvaluator {

	private final Env env;

	public NameEvaluator(IdGenerator generator) {
		env = new Env(generator);
	}

	public Id registerBuiltin(Builtin builtin) {
		return env.registerBuiltinVar(builtin.name(), builtin.type(), builtin.insn());
	}

	public IcModule eval(Module module) {
		env.push();

		// resister toplevels
		module.decls().forEach(decl -> {
			env.registerVar(decl.name(), decl.type());
		});

		List<IcDecl> icDecls = module.decls()
				.stream()
				.map(decl -> eval(decl))
				.toList();

		env.pop();
		return new IcModule(module.name(), icDecls, module.origin());
	}

	public IcDecl eval(Decl decl) {
		Id id = env.get(decl.name());

		env.push();

		IdList idArgs = new IdList();
		List<String> args = decl.args();
		for(int i = 0; i < args.size(); i++) {
			String arg = args.get(i);
			Id argInfo = env.registerArg(id, i, arg, decl.type().apply(i).asArrow().arg());
			idArgs.add(argInfo);
		}

		IcExp icBody = eval(decl.body());

		env.pop();
		return new IcDecl(id, idArgs, id.type(), icBody, decl.loc());
	}

	public IcExp eval(Exp exp) {
		return exp.fold(
				cnst  -> new IcConst(cnst.value(), cnst.loc()),
				id    -> new IcVar(env.get(id.name()), id.loc()),
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
					env.push();
					IcExp result = eval(let.decls(), let.body());
					env.pop();
					return result;
				});
	}

	private IcExp eval(List<Decl> decls, Exp body) {
		decls.forEach(decl -> env.registerVar(decl.name(), decl.type()));

		IcExp retVal = eval(body);
		String filename = body.loc().filename();
		Position end = body.loc().end();

		for(int i = decls.size()-1; i >= 0; i--) {
			Decl decl = decls.get(i);
			retVal = new IcLet(eval(decl), retVal,
					new Location(filename, decl.loc().start(), end));
		}
		return retVal;
	}
}
