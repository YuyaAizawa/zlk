package zlk.common.type;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import zlk.util.Stack;
import zlk.util.pp.PrettyPrintable;

public sealed interface Type extends PrettyPrintable
permits TyUnit, TyBool, TyI32, TyArrow {

	public static final TyUnit unit = TyUnit.SINGLETON;
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
			Function<TyUnit, R> forUnit,
			Function<TyBool, R> forBool,
			Function<TyI32, R> forI32,
			Function<TyArrow, R> forArrow);

	void match(
			Consumer<TyUnit> forUnit,
			Consumer<TyBool> forBool,
			Consumer<TyI32> forI32,
			Consumer<TyArrow> forArrow);

	default TyArrow asArrow() {
		return fold(
				unit  -> null,
				bool  -> null,
				i32   -> null,
				arrow -> arrow);
	}

	default boolean isArrow() {
		return asArrow() != null;
	}

	/**
	 * 指定した個数の引数を適用した後の型を返す．途中でArrowでなくなった場合は例外を投げる．
	 * @param cnt 適用する引数の個数
	 * @return 適用後の型
	 * @throws IllegalArgumentException 指定回数適用できない場合
	 */
	default Type apply(int cnt) {
		Type type = this;
		for(int i = 0; i < cnt ; i++) {
			type = type.fold(
					unit  -> { throw tooManyApplies(this, cnt); },
					bool  -> { throw tooManyApplies(this, cnt); },
					i32   -> { throw tooManyApplies(this, cnt); },
					arrow -> arrow.ret());
		}
		return type;
	}
	private static IllegalArgumentException tooManyApplies(Type type, int cnt) {
		return new IllegalArgumentException(String.format("type: %s, cnt: %d", type, cnt));
	}

	/**
	 * 指定した回数の引数を適用した後に次に引数に取る型を返す．つまりidx番目(0-based)の引数を返す．
	 * @param idx 適用の回数
	 * @return 適用後のArrowの引数型
	 * @throws IllegalArgumentException 指定回数適用できない場合
	 * @throws IllegalStateException 適用後の型が引数を持たない場合
	 */
	default Type arg(int idx) {
		TyArrow fun = apply(idx).asArrow();
		if(fun == null) {
			throw new IllegalStateException(String.format(
					"not have arg. type: %s, idx: %d", this, idx));
		}
		return fun.arg();
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
}
