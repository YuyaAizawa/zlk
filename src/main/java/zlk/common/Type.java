package zlk.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import zlk.common.Type.Arrow;
import zlk.common.Type.Atom;
import zlk.common.Type.Var;
import zlk.common.id.Id;
import zlk.util.Stack;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public sealed interface Type extends PrettyPrintable
permits Atom, Arrow, Var {
	record Atom(Id id) implements Type {}
	record Arrow(Type arg, Type ret) implements Type {}
	record Var(String name) implements Type {}

	public static final Atom UNIT = new Atom(Id.fromCanonicalName("Unit"));
	public static final Atom BOOL = new Atom(Id.fromCanonicalName("Bool"));
	public static final Atom I32  = new Atom(Id.fromCanonicalName("I32"));

	public static Type arrow(Type... rest) {
		if(rest.length < 2) {
			throw new IllegalArgumentException("length: "+rest.length);
		}

		Type tail = rest[rest.length - 1];
		for(int idx = rest.length - 2; idx > 0; idx--) {
			tail = new Arrow(rest[idx], tail);
		}
		return new Arrow(rest[0], tail);
	}

	public static Type arrow(List<Type> args, Type ret) {
		Type result = ret;

		List<Type> args_ = new ArrayList<>(args);
		Collections.reverse(args_);
		for(Type arg : args_) {
			result = new Arrow(arg, result);
		}

		return result;
	}

	public static Type arrow(List<Type> types) {
		return arrow(types.toArray(Type[]::new));
	}

	default Atom asAtom() {
		return switch(this) {
		case Atom atom -> atom;
		default -> null;
		};
	}

	default Arrow asArrow() {
		return switch(this) {
		case Arrow arrow -> arrow;
		default -> null;
		};
	}

	default boolean isArrow() {
		return asArrow() != null;
	}

	/**
	 * 指定した個数の引数を適用した後の型を返す．途中でArrowでなくなった場合は例外を投げる．
	 * @param cnt 適用する引数の個数
	 * @return 適用後の型
	 * @throws IndexOutOfBoundsException 指定回数適用できない場合
	 */
	default Type apply(int cnt) {
		Type result = this;
		for(int i = 0; i < cnt ; i++) {
			result = switch(result) {
			case Atom _ -> throw tooManyApplies(this, cnt);
			case Arrow(_, Type ret) -> ret;
			case Var _ -> throw tooManyApplies(this, cnt);
			};
		}
		return result;
	}
	private static IndexOutOfBoundsException tooManyApplies(Type type, int cnt) {
		return new IndexOutOfBoundsException(String.format(
				"too many applies. type: %s, cnt: %d",
				type,
				cnt));
	}

	/**
	 * 指定した回数の引数を適用した後に次に引数に取る型を返す．つまりidx番目(0-based)の引数を返す．
	 * @param idx 適用の回数
	 * @return 適用後のArrowの引数型
	 * @throws IndexOutOfBoundsException 指定回数適用できないか適用後の型が引数を持たない場合
	 */
	default Type arg(int idx) {
		return switch(apply(idx)) {
		case Atom _ -> throw typeMissmatch(Arrow.class, Atom.class);
		case Arrow(Type arg, _) -> arg;
		case Var _ -> throw typeMissmatch(Arrow.class, Var.class);
		};
	}
	private static IndexOutOfBoundsException typeMissmatch(Class<?> expected, Class<?> actual) {
		return new IndexOutOfBoundsException(String.format(
				"type missmatch. expected: %s, actual: %s",
				expected.getTypeName(),
				actual.getTypeName()));
	}

	default List<Type> flatten() {
		List<Type> flatten = new ArrayList<>();

		Type ret = this;
		while(ret.isArrow()) {
			Arrow fun = ret.asArrow();
			flatten.add(fun.arg());
			ret = fun.ret();
		}
		flatten.add(ret);
		return flatten;
	}

	default Type toTree(List<Type> tail) {
		Stack<Type> stack = new Stack<>();
		stack.push(this);
		tail.forEach(stack::push);

		Type result = stack.pop();
		while(!stack.isEmpty()) {
			result = new Arrow(stack.pop(), result);
		}
		return result;
	}

	@Override
	default void mkString(PrettyPrinter pp) {
		switch(this) {
		case Atom(Id id) -> {
			pp.append(id);
		}
		case Arrow(Type arg, Type ret) -> {
			if(arg.isArrow()) {
				pp.append("(").append(arg).append(")");
			} else {
				pp.append(arg);
			}
			pp.append(" -> ").append(ret);
		}
		case Var(String name) -> {
			pp.append("[").append(name).append("]");
		}
		}
	}
}
