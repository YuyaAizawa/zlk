package zlk.typecheck;

import java.util.List;

import zlk.common.id.IdMap;
import zlk.common.type.TyArrow;
import zlk.common.type.TyAtom;
import zlk.common.type.Type;
import zlk.idcalc.IcApp;
import zlk.idcalc.IcDecl;
import zlk.idcalc.IcExp;
import zlk.idcalc.IcModule;
import zlk.idcalc.IcPattern;
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
			decl.anno().ifPresent(
					anno -> env.put(decl.id(), anno));
		}

		for(IcDecl decl : module.decls()) {
			decl.anno().ifPresent(
					anno -> assertEqual(check(decl), anno, decl.loc()));
		}
		return env;
	}

	public Type check(IcDecl decl) {
		Type anno = decl.anno().get(); // TODO 注釈が無いとき
		System.out.println(decl.id()+": "+anno);

		env.putOrConfirm(decl.id(), anno);

		// TODO パターンのチェック
		Type ret = anno;
		for(IcPattern arg : decl.args()) {
			TyArrow arrow = ret.asArrow();
			env.put(arg.fold(var -> var.id()), arrow.arg());
			ret = arrow.ret();
		}

		try {
			typeAssertion(decl.body(), ret);
			return anno;
		} catch (AssertionError e) {
			throw new AssertionError("in " + decl.id().canonicalName(), e);
		}
	}

	public Type check(IcExp exp) {
		try {
			return exp.fold(
					cnst    -> cnst.type(),
					var     -> {
						Type ty = env.get(var.id());
						if(ty == null) {
							throw new NullPointerException(""+var);
						}
						return ty;
					},
					foreign -> foreign.type(),
					abs     -> {
						env.put(abs.id(), abs.type());
						return Type.arrow(abs.type(), check(abs.body()));
					},
					app     -> {
						List<Type> funTy = check(app.fun()).flatten();
						List<IcExp> args = app.args();

						if(args.size() != funTy.size()-1) {
							throw new AssertionError(String.format(
								"%s takes %d arguments but applied %d.",
								app.fun().loc(),
								funTy.size()-1,
								args.size()));
						}

						for(int i = 0; i < args.size(); i++) {
							typeAssertion(args.get(i), funTy.get(i));
						}

						return funTy.get(funTy.size()-1);
					},
					ifExp   -> {
						typeAssertion(ifExp.cond(), TyAtom.BOOL);
						Type exp1Type = check(ifExp.exp1());
						typeAssertion(ifExp.exp2(), exp1Type);
						return exp1Type;
					},
					let     -> {
						check(let.decl());
						return check(let.body());
					},
					letrec  -> {
						letrec.decls().forEach(this::check);
						return check(letrec.body());
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
