package zlk.common.type;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;

import zlk.util.Stack;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public sealed interface Type extends PrettyPrintable
permits TyVar, TyBool, TyI32, TyArrow {

	public static final TyBool bool = new TyBool();
	public static final TyI32 i32 = new TyI32();
	public static Type arrow(Type... rest) {
		if(rest.length < 2) {
			throw new IllegalArgumentException("length: "+rest.length);
		}

		Type tail = rest[rest.length - 1];
		for(int idx = rest.length - 2; idx > 0; idx--) {
			tail = new TyArrow(rest[idx], tail);
		}
		return new TyArrow(rest[0], tail);
	}
	public static Type arrow(List<Type> types) {
		return arrow(types.toArray(Type[]::new));
	}

	<R> R fold(
			IntFunction<R> forVar,
			Supplier<R> forBool,
			Supplier<R> forI32,
			BiFunction<Type, Type, R> forArrow);

	void match(
			IntConsumer forVar,
			Runnable forBool,
			Runnable forI32,
			BiConsumer<Type, Type> forArrow);

	default TyArrow asArrow() {
		return fold(
				varId      -> null,
				()         -> null,
				()         -> null,
				(arg, ret) -> (TyArrow)this // TODO なんとか
		);
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
		Type type = this;
		for(int i = 0; i < cnt ; i++) {
			type = type.fold(
					varId      -> { throw tooManyApplies(this, cnt); },
					()         -> { throw tooManyApplies(this, cnt); },
					()         -> { throw tooManyApplies(this, cnt); },
					(arg, ret) -> ret);
		}
		return type;
	}
	private static IndexOutOfBoundsException tooManyApplies(Type type, int cnt) {
		return new IndexOutOfBoundsException(String.format("too many applies. type: %s, cnt: %d", type, cnt));
	}

	/**
	 * 指定した回数の引数を適用した後に次に引数に取る型を返す．つまりidx番目(0-based)の引数を返す．
	 * @param idx 適用の回数
	 * @return 適用後のArrowの引数型
	 * @throws IndexOutOfBoundsException 指定回数適用できないか適用後の型が引数を持たない場合
	 */
	default Type arg(int idx) {
		try {
			return apply(idx).fold(
					varId      -> { throw new IndexOutOfBoundsException(); },
					()         -> { throw new IndexOutOfBoundsException(); },
					()         -> { throw new IndexOutOfBoundsException(); },
					(arg, ret) -> arg
			);
		} catch(IndexOutOfBoundsException e) {
			throw new IndexOutOfBoundsException(String.format(
					"no arg. type: %s, idx: %d", this, idx));
		}
	}

	default List<Type> flatten() {
		List<Type> flatten = new ArrayList<>();

		Type ret = this;
		while(ret.isArrow()) {
			TyArrow fun = ret.asArrow();
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
			result = new TyArrow(stack.pop(), result);
		}
		return result;
	}

	@Override
	default void mkString(PrettyPrinter pp) {
		match(
				varId      -> pp.append("[").append(varId).append("]"),
				()         -> pp.append("Bool"),
				()         -> pp.append("I32"),
				(arg, ret) -> {
					if(arg.isArrow()) {
						pp.append("(").append(arg).append(")");
					} else {
						pp.append(arg);
					}
					pp.append(" -> ").append(ret);
				});
	}
}
