package zlk.typecheck;

import java.util.List;

import zlk.common.id.IdMap;
import zlk.common.type.TyArrow;
import zlk.common.type.TyAtom;
import zlk.common.type.Type;
import zlk.idcalc.IcApp;
import zlk.idcalc.IcCaseBranch;
import zlk.idcalc.IcDecl;
import zlk.idcalc.IcExp;
import zlk.idcalc.IcModule;
import zlk.idcalc.IcPCtorArg;
import zlk.idcalc.IcPattern;
import zlk.util.Location;

public final class TypeChecker {

	private final IdMap<Type> env;

	public static Type check(IcApp app, IdMap<Type> type) {
		TypeChecker tmp = new TypeChecker(type);
		return tmp.check(app);
	}

	public TypeChecker(IdMap<Type> expected) {
		env = expected;
	}

	public void check(IcModule module) {
		for(IcDecl decl : module.decls()) {
			check(decl);
		}
	}

	public Type check(IcDecl decl) {
		Type ret = check(decl.body());
		for(IcPattern arg : decl.args()) {
			ret = new TyArrow(check(arg), ret);
		}

		try {
			assertEqual(ret, env.get(decl.id()), decl.loc());
			return ret;
		} catch (AssertionError e) {
			throw new AssertionError("in " + decl.id().canonicalName(), e);
		}
	}

	private Type check(IcPattern pattern) {
		return pattern.fold(
				var -> env.get(var.id()),
				ctor -> {
					Type ctorType = env.get(ctor.ctor().id());
					List<Type> argTypes = ctorType.flatten();

					for (int i = 0; i < ctor.args().size(); i++) {
						IcPCtorArg arg = ctor.args().get(i);
						typeAssertion(arg.pattern(), arg.type());
						assertEqual(arg.type(), argTypes.get(i), arg.pattern().loc());
					}
					return argTypes.get(argTypes.size()-1);
				});
	}

	public Type check(IcExp exp) {
		try {
			return exp.fold(
					cnst    -> cnst.type(),
					var     -> env.get(var.id()),
					foreign -> env.get(foreign.id()),
					ctor    -> env.get(ctor.id()),
					abs     -> Type.arrow(env.get(abs.id()), check(abs.body())),
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
					},
					case_  -> {
						Type target = check(case_.target());
						Type patternType = check(case_.branches().get(0).pattern());
						assertEqual(patternType, target, case_.branches().get(0).pattern().loc());
						Type bodyType = check(case_.branches().get(0).body());
						for(IcCaseBranch branch: case_.branches()) {
							typeAssertion(branch.pattern(), patternType);
							typeAssertion(branch.body(), bodyType);
						}
						return bodyType;
					});
		} catch(NullPointerException e) {
			throw new RuntimeException("on "+exp.loc(), e);
		}
	}

	private void typeAssertion(IcExp exp, Type expected) {
		assertEqual(check(exp), expected, exp.loc());
	}

	private void typeAssertion(IcPattern pat, Type expected) {
		assertEqual(check(pat), expected, pat.loc());
	}

	private static void assertEqual(Type actual, Type expected, Location loc) {
		if(!actual.equals(expected)) {
			throw new AssertionError(String.format("expect: %s, actual: %s @%s", expected, actual, loc));
		}
	}
}
