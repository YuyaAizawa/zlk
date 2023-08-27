package zlk.nameeval;

import java.util.List;
import java.util.Optional;

import zlk.ast.AType;
import zlk.ast.Decl;
import zlk.ast.Exp;
import zlk.ast.Module;
import zlk.common.id.Id;
import zlk.common.id.IdMap;
import zlk.common.type.TyArrow;
import zlk.common.type.TyAtom;
import zlk.common.type.Type;
import zlk.core.Builtin;
import zlk.idcalc.IcApp;
import zlk.idcalc.IcCnst;
import zlk.idcalc.IcDecl;
import zlk.idcalc.IcExp;
import zlk.idcalc.IcForeign;
import zlk.idcalc.IcIf;
import zlk.idcalc.IcLet;
import zlk.idcalc.IcLetrec;
import zlk.idcalc.IcModule;
import zlk.idcalc.IcPVar;
import zlk.idcalc.IcPattern;
import zlk.idcalc.IcVar;
import zlk.util.Location;
import zlk.util.Position;

public final class NameEvaluator {

	private final Module module;
	private final Env env;
	private final TyEnv tyEnv;
	private final IdMap<Type> builtinTy;

	public NameEvaluator(Module module, List<Builtin> builtinFuncs) {
		this.module = module;
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
		try {
			String declName = decl.name();
			env.push(declName);

			Id id = env.get(declName);
			Optional<Type> anno = decl.anno().map(a -> eval(a));
			List<IcPattern> args = decl.args().stream()
					.map(arg -> {
						Id id_ = env.registerVar(arg.name());
						return IcPattern.var(new IcPVar(id_, arg.loc()));
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
					Type ty = builtinTy.getOrNull(id);
					if(ty == null) {
						return new IcVar(id, var.loc());
					} else {
						return new IcForeign(id, ty, var.loc());
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

	private Type eval(AType aTy) {
		return aTy.fold(
				base -> tyEnv.get(base.name()),
				arrow -> new TyArrow(eval(arrow.arg()), eval(arrow.ret())));
	}
}
