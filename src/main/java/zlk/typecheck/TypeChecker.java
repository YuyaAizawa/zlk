package zlk.typecheck;

import java.util.List;

import zlk.common.ConstValue;
import zlk.common.Type;
import zlk.common.id.Id;
import zlk.common.id.IdMap;
import zlk.idcalc.IcCaseBranch;
import zlk.idcalc.IcValDecl;
import zlk.idcalc.IcExp;
import zlk.idcalc.IcExp.IcAbs;
import zlk.idcalc.IcExp.IcApp;
import zlk.idcalc.IcExp.IcCase;
import zlk.idcalc.IcExp.IcCnst;
import zlk.idcalc.IcExp.IcIf;
import zlk.idcalc.IcExp.IcLet;
import zlk.idcalc.IcExp.IcLetrec;
import zlk.idcalc.IcExp.IcVarCtor;
import zlk.idcalc.IcExp.IcVarForeign;
import zlk.idcalc.IcExp.IcVarLocal;
import zlk.idcalc.IcModule;
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
		for(IcValDecl decl : module.decls()) {
			check(decl);
		}
	}

	public Type check(IcValDecl decl) {
		Type ret = check(decl.body());
		for(IcPattern arg : decl.args()) {
			ret = new Type.Arrow(check(arg), ret);
		}

		try {
			assertEqual(ret, env.get(decl.id()), decl.loc());
			return ret;
		} catch (AssertionError e) {
			throw new AssertionError("in " + decl.id().canonicalName(), e);
		}
	}

	private Type check(IcPattern pattern) {
		return switch(pattern) {
		case IcPattern.Var(Id id, Location _) -> {
			yield env.get(id);
		}
		case IcPattern.Ctor(IcExp.IcVarCtor ctor, List<IcPattern.Arg> args, Location _) -> {
			Type ctorType = env.get(ctor.id());
			List<Type> argTypes = ctorType.flatten();

			for (int i = 0; i < args.size(); i++) {
				IcPattern.Arg arg = args.get(i);
				typeAssertion(arg.pattern(), arg.type());
				assertEqual(arg.type(), argTypes.get(i), arg.pattern().loc());
			}
			yield argTypes.get(argTypes.size()-1);
		}
		};
	}

	public Type check(IcExp exp) {
		try {
			return switch (exp) {
			case IcCnst(ConstValue value, Location _) -> value.type();
			case IcVarLocal(Id id, Location _) -> env.get(id);
			case IcVarForeign(Id id, Type _, Location _) -> env.get(id);
			case IcVarCtor(Id id, Type _, Location _) -> env.get(id);
			case IcAbs(Id id, Type _, IcExp body, Location _) -> {
				yield Type.arrow(env.get(id), check(body));
			}
			case IcApp(IcExp fun, List<IcExp> args, Location _) -> {
				List<Type> funTy = check(fun).flatten();

				if(args.size() != funTy.size()-1) {
					throw new AssertionError(String.format(
						"%s takes %d arguments but applied %d.",
						fun.loc(),
						funTy.size()-1,
						args.size()));
				}

				for(int i = 0; i < args.size(); i++) {
					typeAssertion(args.get(i), funTy.get(i));
				}

				yield funTy.get(funTy.size()-1);
			}
			case IcIf(IcExp cond, IcExp thenExp, IcExp elseExp, Location _) -> {
				typeAssertion(cond, Type.BOOL);
				Type thenType = check(thenExp);
				typeAssertion(elseExp, thenType);
				yield thenType;
			}
			case IcLet(IcValDecl decl, IcExp body, Location _) -> {
				check(decl);
				yield check(body);
			}
			case IcLetrec(List<IcValDecl> decls, IcExp body, Location _) -> {
				for(IcValDecl decl : decls) {
					check(decl);
				}
				yield check(body);
			}
			case IcCase(IcExp target, List<IcCaseBranch> branches, Location _) -> {
				Type targetType = check(target);
				Type patternType = check(branches.get(0).pattern());
				assertEqual(patternType, targetType, branches.get(0).pattern().loc());
				Type bodyType = check(branches.get(0).body());
				for(IcCaseBranch branch: branches) {
					typeAssertion(branch.pattern(), patternType);
					typeAssertion(branch.body(), bodyType);
				}
				yield bodyType;
			}
			};
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
