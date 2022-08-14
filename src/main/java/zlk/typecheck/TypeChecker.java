package zlk.typecheck;

import java.util.List;

import zlk.common.TyArrow;
import zlk.common.Type;
import zlk.idcalc.IcDecl;
import zlk.idcalc.IcExp;
import zlk.idcalc.IdInfo;

public final class TypeChecker {
	private TypeChecker() {}

	public static Type check(IcDecl decl) {
		List<IdInfo> args = decl.args();

		for (int i = 0; i < args.size(); i++) {
			assertEqual(args.get(i).type(), decl.type().nth(i));
		}

		assertEqual(check(decl.body()), decl.type().nth(args.size()));

		return decl.type();
	}

	private static Type check(IcExp exp) {
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
					if(check(let.decl()).isArrow()) {
						throw new AssertionError(String.format("let only binds I32. %s : %s", let.decl().id(), let.decl().type()));
					}
					return check(let.body());
				});
	}

	private static void assertEqual(Type actual, Type expected) {
		if(!actual.equals(expected)) {
			throw new AssertionError(String.format("expect: %s, actual: %s", expected, actual));
		}
	}
}
