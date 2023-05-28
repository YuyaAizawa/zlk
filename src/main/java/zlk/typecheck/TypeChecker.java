package zlk.typecheck;

import zlk.common.id.IdMap;
import zlk.common.type.TyArrow;
import zlk.common.type.TyBase;
import zlk.common.type.Type;
import zlk.idcalc.IcApp;
import zlk.idcalc.IcDecl;
import zlk.idcalc.IcExp;
import zlk.idcalc.IcModule;
import zlk.util.Location;

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
			assertEqual(check(decl), decl.type(), decl.loc());
		}
		return env;
	}

	public Type check(IcDecl decl) {
		env.putOrConfirm(decl.id(), decl.type());
		System.out.println(decl.id()+": "+decl.type());

		try {
			assertEqual(check(decl.body()), decl.type(), decl.body().loc());

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
					abs   -> {
						env.put(abs.id(), abs.type());
						return Type.arrow(abs.type(), check(abs.body()));
					},
					app   -> {
						TyArrow arrow = check(app.fun()).asArrow();
						if(arrow == null) {
							throw new AssertionError(String.format(
									"%s takes no more arguments but applied %s.",
									app.fun().loc(),
									app.arg().loc()));
						}
						typeAssertion(app.arg(), arrow.arg());
						return arrow.ret();
					},
					ifExp -> {
						typeAssertion(ifExp.cond(), TyBase.BOOL);
						Type exp1Type = check(ifExp.exp1());
						typeAssertion(ifExp.exp2(), exp1Type);
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

	private void typeAssertion(IcExp exp, Type expected) {
		assertEqual(check(exp), expected, exp.loc());
	}

	private static void assertEqual(Type actual, Type expected, Location loc) {
		if(!actual.equals(expected)) {
			throw new AssertionError(String.format("expect: %s, actual: %s @%s", expected, actual, loc));
		}
	}
}
