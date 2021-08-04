package zlk.typecheck;

import java.util.List;

import zlk.common.TyArrow;
import zlk.common.Type;
import zlk.idcalc.IcDecl;
import zlk.idcalc.IcExp;
import zlk.idcalc.IcVar;

public final class TypeChecker {
	private TypeChecker() {}

	public static Type check(IcDecl decl) {
		List<IcVar> args = decl.args();

		for (int i = 0; i < args.size(); i++) {
			assertEqual(args.get(i).idInfo().type(), nth(decl.type(), i));
		}

		assertEqual(check(decl.body()), nth(decl.type(), args.size()));

		return decl.type();
	}

	private static Type check(IcExp exp) {
		return exp.fold(
				cnst -> cnst.type(),
				var  -> var.idInfo().type(),
				app  -> {
					Type funType = check(app.fun());
					Type restType = funType;
					for(IcExp arg : app.args()) {
						TyArrow arrow = restType.asArrow();
						if(arrow == null) {
							throw new AssertionError(String.format(
									"too many arguments. fun: %s, type: %s, args: %s",
									app.fun().mkString(),
									funType.mkString(),
									app.args().stream().map(IcExp::mkString).toList()));
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
				});
	}

	private static void assertEqual(Type actual, Type expected) {
		if(!actual.equals(expected)) {
			throw new AssertionError(String.format("expect: %s, actual: %s", expected, actual));
		}
	}

	private static Type nth(Type type, int idx) {
		int rest = idx;
		while(rest > 0) {
			type = type.map(
					unit  -> { throw new IndexOutOfBoundsException(idx); },
					bool  -> { throw new IndexOutOfBoundsException(idx); },
					i32   -> { throw new IndexOutOfBoundsException(idx); },
					arrow -> arrow.ret());
			rest--;
		}
		return type.map(
				unit  -> unit,
				bool  -> bool,
				i32   -> i32,
				arrow -> arrow.arg());
	}
}
