package zlk.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import zlk.common.Type.Arrow;
import zlk.common.Type.CtorApp;
import zlk.common.Type.Var;
import zlk.common.id.Id;
import zlk.util.Stack;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

/**
 * 型
 * 主に型注釈などで利用する
 *
 * <ul>
 *   <li> {@link CtorApp} -- 関数型以外の型
 *   <li> {@link Arrow} -- 関数型
 *   <li> {@link Var} -- 型変数
 * </ul>
 */
public sealed interface Type extends PrettyPrintable
permits CtorApp, Arrow, Var {

	/**
	 * 関数型以外の型
	 * @param ctor 型構築子
	 * @param args 型パラメータ
	 */
	record CtorApp(Id ctor, List<Type> args) implements Type {
		public CtorApp(Id id) {
			this(id, List.of());
		}
	}

	/**
	 * 関数型
	 * @param arg 引数の型
	 * @param ret 戻り値の型
	 */
	record Arrow(Type arg, Type ret) implements Type {

		/**
		 * 複数引数として見たときの引数型を返す．
		 * @return 複数引数として見た引数の型
		 */
		public List<Type> args() {
			List<Type> args = new ArrayList<>();

			Type ret = this;
			while(ret instanceof Arrow fun) {
				args.add(fun.arg());
				ret = fun.ret();
			}
			return args;
		}
	}

	/**
	 * 型変数
	 * @param name 変数名
	 */
	record Var(String name) implements Type {}

	public static final CtorApp UNIT = new CtorApp(Id.fromCanonicalName("Unit"));
	public static final CtorApp BOOL = new CtorApp(Id.fromCanonicalName("Bool"));
	public static final CtorApp I32  = new CtorApp(Id.fromCanonicalName("I32"));

	public static final List<CtorApp> BUILTIN = List.of(UNIT, BOOL, I32);

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

	default CtorApp asAtom() {
		return switch(this) {
		case CtorApp atom -> atom;
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
			case CtorApp _ -> throw tooManyApplies(this, cnt);
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
		case CtorApp _ -> throw typeMissmatch(Arrow.class, CtorApp.class);
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
		while(ret instanceof Arrow fun) {
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

	default List<String> getVarNames() {
		List<String> result = new ArrayList<>();
		getVarNamesHelp(result);
		return result;
	}
	default void getVarNamesHelp(List<String> acc) {
		switch(this) {
		case CtorApp(_, List<Type> typeArguments) -> {
			for(Type arg : typeArguments) {
				arg.getVarNamesHelp(acc);
			}
		}
		case Arrow(Type arg, Type ret) -> {
			arg.getVarNamesHelp(acc);
			ret.getVarNamesHelp(acc);
		}
		case Var(String name) -> {
			if(!acc.contains(name)) {
				acc.add(name);
			}
		}
		}
	}

	@Override
	default void mkString(PrettyPrinter pp) {
		switch(this) {
		case CtorApp(Id id, List<Type> typeArguments) -> {
			pp.append(id);
			typeArguments.forEach(a -> {
				if(a instanceof Arrow ||
						(a instanceof CtorApp(_, List<Type> args) && !args.isEmpty())) {
					pp.append(" (").append(a).append(")");
				} else {
					pp.append(" ").append(a);
				}
			});
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
			pp.append(name);
		}
		}
	}
}
