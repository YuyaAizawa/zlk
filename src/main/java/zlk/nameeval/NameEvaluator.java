package zlk.nameeval;

import java.util.ArrayList;
import java.util.List;

import zlk.ast.Decl;
import zlk.ast.Exp;
import zlk.ast.Module;
import zlk.common.Id;
import zlk.common.IdGenerator;
import zlk.common.IdList;
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
			Id argInfo = env.registerArg(id, i, arg, getArgType(decl.type(), i));
			idArgs.add(argInfo);
		}

		IcExp icBody = eval(decl.body());

		env.pop();
		return new IcDecl(id, idArgs, id.type(), icBody);
	}

	public IcExp eval(Exp exp) {
		return exp.fold(
				cnst  -> new IcConst(cnst),
				id    -> new IcVar(env.get(id.name())),
				app   -> {
					List<Exp> exps = app.exps();
					IcExp icFun = eval(exps.get(0));
					List<IcExp> icArgs = new ArrayList<>();
					for(int i = 1; i < exps.size(); i++) {
						icArgs.add(eval(exps.get(i)));
					}
					return new IcApp(icFun, icArgs);
				},
				ifExp -> new IcIf(
						eval(ifExp.cond()),
						eval(ifExp.exp1()),
						eval(ifExp.exp2())),
				let -> {
					env.push();
					IcExp result = eval(let.decls(), 0, let.body());
					env.pop();
					return result;
				});
	}

	private IcExp eval(List<Decl> decls, int i, Exp body) {
		if(i < decls.size()) {
			Decl decl = decls.get(i);
			env.registerVar(decl.name(), decl.type());
			return new IcLet(eval(decl), eval(decls, i+1, body));
		} else {
			return eval(body);
		}
	}

	private Type getArgType(Type funType, int index) {
		return funType.fold(
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
