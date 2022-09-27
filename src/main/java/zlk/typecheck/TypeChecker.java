package zlk.typecheck;

import zlk.common.id.IdList;
import zlk.common.id.IdMap;
import zlk.common.type.TyArrow;
import zlk.common.type.Type;
import zlk.idcalc.IcApp;
import zlk.idcalc.IcDecl;
import zlk.idcalc.IcExp;
import zlk.idcalc.IcModule;

public final class TypeChecker {
	private IdMap<Type> env;

	public static Type check(IcApp app, IdMap<Type> type) {
		TypeChecker tmp = new TypeChecker(type);
		return tmp.check(app);
	}

	public TypeChecker(IdMap<Type> foreign) {
		env = foreign.clone();
	}

	public IdMap<Type> check(IcModule module) {
		for(IcDecl decl : module.decls()) {
			env.put(decl.id(), decl.type());
		}

		for(IcDecl decl : module.decls()) {
			assertEqual(check(decl), decl.type());
		}
		return env;
	}

	public Type check(IcDecl decl) {
		env.putOrConfirm(decl.id(), decl.type());
		System.out.println(decl.id()+": "+decl.type());

		try {
			IdList args = decl.args();
			for (int i = 0; i < args.size(); i++) {
				env.put(args.get(i), decl.type().arg(i));
			}

			assertEqual(check(decl.body()), decl.type().apply(args.size()));

			return decl.type();
		} catch (AssertionError e) {
			throw new AssertionError("in " + decl.id().canonicalName(), e);
		}
	}

	public Type check(IcExp exp) {
		try {
			return exp.fold(
					cnst  -> cnst.type(),
					var   -> {
						Type ty = env.get(var.id());
						if(ty == null) {
							throw new NullPointerException(""+var);
						}
						return ty;
					},
					app   -> {
						Type funType = check(app.fun());
						Type restType = funType;
						for(IcExp arg : app.args()) {

							TyArrow arrow = restType.asArrow();
							if(arrow == null) {
								throw new AssertionError(String.format(
										"too many arguments. fun: %s, type: %s, args: %s",
										app.fun().toString(),
										funType.toString(),
										app.args().size()));
							}
							assertEqual(check(arg), arrow.arg());
							restType = arrow.ret();
						}
						return restType;
					},
					ifExp -> {
						assertEqual(check(ifExp.cond()), Type.bool);
						Type exp1Type = check(ifExp.exp1());
						assertEqual(check(ifExp.exp2()), exp1Type);
						return exp1Type;
					},
					let   -> {
						check(let.decl());
						return check(let.body());
					});
		} catch(NullPointerException e) {
			throw new RuntimeException("on "+exp.loc(), e);
		}
	}

	private static void assertEqual(Type actual, Type expected) {
		if(!actual.equals(expected)) {
			throw new AssertionError(String.format("expect: %s, actual: %s", expected, actual));
		}
	}
}
