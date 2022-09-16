package zlk.typecheck;

import zlk.common.id.IdList;
import zlk.common.type.TyArrow;
import zlk.common.type.Type;
import zlk.idcalc.IcDecl;
import zlk.idcalc.IcExp;

public final class TypeChecker {
	private TypeChecker() {}

	public static Type check(IcDecl decl) {
		try {
			IdList args = decl.args();

			for (int i = 0; i < args.size(); i++) {
				assertEqual(args.get(i).type(), decl.type().arg(i));
			}

			assertEqual(check(decl.body()), decl.type().apply(args.size()));

			return decl.type();
		} catch (AssertionError e) {
			throw new AssertionError("in " + decl.id().name(), e);
		}
	}

	public static Type check(IcExp exp) {
		return exp.fold(
				cnst  -> cnst.type(),
				var   -> var.idInfo().type(),
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
					return check(let.body());
				});
	}

	private static void assertEqual(Type actual, Type expected) {
		if(!actual.equals(expected)) {
			throw new AssertionError(String.format("expect: %s, actual: %s", expected, actual));
		}
	}
}
