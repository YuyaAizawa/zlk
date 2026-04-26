package zlk.common;

import java.util.ArrayList;

import zlk.common.Type.Arrow;
import zlk.common.Type.CtorApp;
import zlk.common.Type.Var;
import zlk.common.id.Id;
import zlk.util.collection.Seq;
import zlk.util.collection.SeqBuffer;
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
	record CtorApp(Id ctor, Seq<Type> args) implements Type {
		public CtorApp(Id id) {
			this(id, Seq.of());
		}

		@Override
		public final String toString() {
			return buildString();
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
		public Seq<Type> args() {
			SeqBuffer<Type> result = new SeqBuffer<>();

			Type ret = this;
			while(ret instanceof Arrow fun) {
				result.add(fun.arg());
				ret = fun.ret();
			}
			return result.toSeq();
		}

		@Override
		public final String toString() {
			return buildString();
		}
	}

	/**
	 * 型変数
	 * @param name 変数名
	 */
	record Var(String name) implements Type {
		@Override
		public final String toString() {
			return buildString();
		}
	}

	public static final CtorApp UNIT = new CtorApp(Id.intern("Unit"));
	public static final CtorApp BOOL = new CtorApp(Id.intern("Bool"));
	public static final CtorApp I32  = new CtorApp(Id.intern("I32"));

	public static final Seq<CtorApp> BUILTIN = Seq.of(UNIT, BOOL, I32);

	public static Type.Arrow arrow(Type... rest) {
		if(rest.length < 2) {
			throw new IllegalArgumentException("length: "+rest.length);
		}

		Type tail = rest[rest.length - 1];
		for(int idx = rest.length - 2; idx > 0; idx--) {
			tail = new Arrow(rest[idx], tail);
		}
		return new Arrow(rest[0], tail);
	}

	public static Type.Arrow arrow(Seq<Type> args, Type ret) {
		Seq<Type> argsReversed = args.reversed();
		Type.Arrow result = new Arrow(argsReversed.head(), ret);

		for(Type arg : argsReversed.tail()) {
			result = new Arrow(arg, result);
		}

		return result;
	}

	public static Type.Arrow arrow(Seq<Type> types) {
		int size = types.size();
		if(size < 2) {
			throw new IllegalArgumentException("types.size(): "+size);
		}
		return arrow(types.dropLast(), types.last());
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
	 * 指定した個数の引数を除いた残りの型を返す．途中でArrowでなくなった場合は例外を投げる．
	 * @param cnt 除去する引数の個数
	 * @return 除去後の型
	 * @throws IndexOutOfBoundsException 指定回数適用できない場合
	 */
	default Type dropArgs(int cnt) {
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
	 * idx番目(0-based)の引数を返す．
	 * @param idx インデックス
	 * @return 指定した位置の型
	 * @throws IndexOutOfBoundsException 指定回数適用できないか適用後の型が引数を持たない場合
	 */
	default Type arg(int idx) {
		return switch(dropArgs(idx)) {
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

	default Seq<Type> flatten() {
		SeqBuffer<Type> flatten = new SeqBuffer<>();

		Type ret = this;
		while(ret instanceof Arrow fun) {
			flatten.add(fun.arg());
			ret = fun.ret();
		}
		flatten.add(ret);
		return flatten.toSeq();
	}

	public static Type fromSeq(Seq<Type> tys) {
		if(tys.isEmpty()) {
			throw new IllegalArgumentException();
		}

		Seq<Type> revTys = tys.reversed();

		Type result = revTys.head();
		for(Type ty : revTys.tail()) {
			result = new Arrow(ty, result);
		}
		return result;
	}

	default Seq<String> getVarNames() {
		SeqBuffer<String> result = new SeqBuffer<>();
		getVarNamesHelp(result);
		return result.toSeq();
	}
	default void getVarNamesHelp(SeqBuffer<String> acc) {
		switch(this) {
		case CtorApp(_, Seq<Type> typeArguments) -> {
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

	/**
	 * この関数型を指定した型に適用した戻り値型を返す．
	 * @param arg
	 * @return
	 */
	default Type apply(Type arg) {
		return switch(this) {
		case CtorApp _ -> throw invalidTypeApply(this, arg);
		case Arrow(Type pattern, Type ret) -> {
			try {
				BindList binds = new BindList();
				pattern.bind(arg, binds);
				yield ret.subst(binds);
			} catch (IllegalArgumentException e) {
				throw invalidTypeApply(this, arg);
			}
		}
		case Var _ -> throw invalidTypeApply(this, arg);
		};
	}
	private static IllegalArgumentException invalidTypeApply(Type fun, Type arg) {
		return new IllegalArgumentException(
				String.format("Invalid type apply. fun: %s, arg: %s", fun, arg));
	}
	record BindPair(String name, Type ty) {}
	static class BindList extends ArrayList<BindPair> {  // TODO IdMapに統合できないか
		Type getOrNull(String name) {
			for(BindPair registered : this) {
				if(registered.name.equals(name)) {
					return registered.ty;
				}
			}
			return null;
		}
		void putOrConfirm(String name, Type ty) {
			Type prev = getOrNull(name);
			if(prev == null) {
				add(new BindPair(name, ty));
			} else if(prev.equals(ty)) {
				// OK
			} else {
				throw new IllegalArgumentException(
						String.format("var conflicted. prev: %s, new: %s", prev, ty));
			}
		}
	}
	default void bind(Type target, BindList binds) {
		switch(this) {
		case Var(String name) -> {
			binds.putOrConfirm(name, target);
		}
		case CtorApp(Id ctor, Seq<Type> args) -> {
			if(target instanceof CtorApp(Id targetCtor, Seq<Type> targetArgs)) {
				if(ctor.equals(targetCtor)) {
					if(args.size() != targetArgs.size()) {
						throw new IllegalArgumentException(
								String.format("Invalid type bind. this: %s, target: %s", this, target));
					}
					args.forEachIndexed((i, arg) -> arg.bind(targetArgs.at(i), binds));
					return;
				}
			}
			throw new IllegalArgumentException(
					String.format("Invalid type bind. this: %s, target: %s", this, target));
		}
		case Arrow(Type arg, Type ret) -> {
			if(target instanceof Arrow(Type targetArg, Type targetRet)) {
				arg.bind(targetArg, binds);
				ret.bind(targetRet, binds);
				return;
			}
			throw new IllegalArgumentException(
					String.format("Invalid type bind. this: %s, target: %s", this, target));
		}
		}
	}
	default Type subst(BindList binds) {
		return switch(this) {
		case Var(String name) -> {
			Type bound = binds.getOrNull(name);
			yield (bound != null) ? bound : this;
		}
		case CtorApp(Id ctor, Seq<Type> args) -> {
			yield new CtorApp(ctor, args.map(t -> t.subst(binds)));
		}
		case Arrow(Type arg, Type ret) -> {
			yield new Arrow(arg.subst(binds), ret.subst(binds));
		}
		};
	}

	@Override
	default void mkString(PrettyPrinter pp) {
		switch(this) {
		case CtorApp(Id id, Seq<Type> typeArguments) -> {
			pp.append(id);
			typeArguments.forEach(a -> {
				if(a instanceof Arrow ||
						(a instanceof CtorApp(_, Seq<Type> args) && !args.isEmpty())) {
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
