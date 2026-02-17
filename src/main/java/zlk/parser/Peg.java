package zlk.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * ソースからオブジェクトを読み取る解析表現文法パーサ.
 * パーサは他のパーサと合成できる．
 *
 * @param <T> 解析結果オブジェクトの型
 */
public abstract class Peg<T> {

	/**
	 * パースが成功したが特に返すものが無いときに使う型
	 */
	public enum Unit {
		INSTANCE;
	}

	/**
	 * ソースからオブジェクトを読み取る．
	 *
	 * パースはソースの現在の読出し開始位置から行わる．
	 * 呼出し後の読出し開始は，解析成功なら解析が終了した位置に進み，失敗時は未定義．
	 *
	 * @param src
	 * @return 解析結果（解析失敗時はnull）
	 */
	public abstract T parse(Tokenized src);

	public <R> Peg<R> map(Function<? super T, ? extends R> mapper) {
		return new Peg<>() {
			@Override
			public R parse(Tokenized src) {
				T t = Peg.this.parse(src);
				if(t == null) {
					return null;
				}
				return mapper.apply(t);
			}
		};
	}

	public static <T> Peg<T> lazy(
			Supplier<Peg<? extends T>> supplier
	) {
		return new Peg<>() {
			Peg<? extends T> peg = null;

			@Override
			public T parse(Tokenized src) {
				if(peg == null) {
					peg = supplier.get();
				}
				return peg.parse(src);
			}

		};
	}

	public static Peg<Token> kind(Token.Kind kind) {
		return new Peg<>() {
			@Override
			public Token parse(Tokenized src) {
				if(src.hasNext() && src.peek().kind == kind) {
					return src.next();
				}
				return null;
			}
		};
	}

	public static <R, T1, T2> Peg<R> sequence(
			Peg<T1> p1,
			Peg<T2> p2,
			BiFunction<? super T1, ? super T2, ? extends R> combiner) {
		return new Peg<>() {
			@Override
			public R parse(Tokenized src) {
				T1 r1 = p1.parse(src);
				if(r1 == null) {
					return null;
				}
				T2 r2 = p2.parse(src);
				if(r2 == null) {
					return null;
				}
				return combiner.apply(r1, r2);
			}
		};
	}

	@SuppressWarnings("unchecked")
	private static <T> Peg<T> choiceBase(
			Peg<? extends T>... candidates
	) {
		return new Peg<>() {
			@Override
			public T parse(Tokenized src) {
				int start = src.mark();
				T r = null;
				for(Peg<? extends T> p : candidates) {
					r = p.parse(src);
					if(r != null) {
						return r;
					}
					src.jump(start);
				}
				return null;
			}
		};
	}

	@SuppressWarnings("unchecked")
	public static <T> Peg<T> choice(
			Peg<? extends T> s1,
			Peg<? extends T> s2
	) {
		return choiceBase(s1, s2);
	}

	@SuppressWarnings("unchecked")
	public static <T> Peg<T> choice(
			Peg<? extends T> s1,
			Peg<? extends T> s2,
			Peg<? extends T> s3
	) {
		return choiceBase(s1, s2, s3);
	}

	@SuppressWarnings("unchecked")
	public static <T> Peg<T> choice(
			Peg<? extends T> s1,
			Peg<? extends T> s2,
			Peg<? extends T> s3,
			Peg<? extends T> s4
	) {
		return choiceBase(s1, s2, s3, s4);
	}

	@SuppressWarnings("unchecked")
	public static <T> Peg<T> choice(
			Peg<? extends T> s1,
			Peg<? extends T> s2,
			Peg<? extends T> s3,
			Peg<? extends T> s4,
			Peg<? extends T> s5
	) {
		return choiceBase(s1, s2, s3, s4, s5);
	}

	public static <T> Peg<List<T>> star(Peg<T> p) {
		return new Peg<>() {
			@Override
			public List<T> parse(Tokenized src) {
				List<T> result = new ArrayList<>();
				while(true) {
					int start = src.mark();
					T r = p.parse(src);
					if(r == null) {
						src.jump(start);
						return result;
					}
					result.add(r);
				}
			}
		};
	}

	public static <T> Peg<List<T>> plus(Peg<T> p) {
		return new Peg<>() {
			@Override
			public List<T> parse(Tokenized src) {
				List<T> result = new ArrayList<>();
				T r = p.parse(src);
				if(r == null) {
					return null;
				}
				result.add(r);
				while(true) {
					int start = src.mark();
					r = p.parse(src);
					if(r == null) {
						src.jump(start);
						return result;
					}
					result.add(r);
				}
			}
		};
	}

	public static <T> Peg<Optional<T>> optional(Peg<T> p) {
		return new Peg<>() {
			@Override
			public Optional<T> parse(Tokenized src) {
				int start = src.mark();
				T r = p.parse(src);
				if(r == null) {
					src.jump(start);
					return Optional.empty();
				}
				return Optional.of(r);
			}
		};
	}

	public static Peg<Unit> andPred(Peg<?> p) {
		return new Peg<>() {
			@Override
			public Unit parse(Tokenized src) {
				int mark = src.mark();
				if(p.parse(src) != null) {
					src.jump(mark);
					return Unit.INSTANCE;
				}
				return null;
			}
		};
	}

	public static Peg<Unit> notPred(Peg<?> p) {
		return new Peg<>() {
			@Override
			public Unit parse(Tokenized src) {
				int mark = src.mark();
				if(p.parse(src) == null) {
					src.jump(mark);
					return Unit.INSTANCE;
				}
				return null;
			}
		};
	}
	public static <R, T1, T2, T3> Peg<R> sequence(
			Peg<T1> p1,
			Peg<T2> p2,
			Peg<T3> p3,
			Function3<? super T1, ? super T2, ? super T3, ? extends R> combiner) {
		return new Peg<>() {
			@Override
			public R parse(Tokenized src) {
				T1 r1 = p1.parse(src);
				if(r1 == null) {
					return null;
				}
				T2 r2 = p2.parse(src);
				if(r2 == null) {
					return null;
				}
				T3 r3 = p3.parse(src);
				if(r3 == null) {
					return null;
				}
				return combiner.apply(r1, r2, r3);
			}
		};
	}
	public static <R, T1, T2, T3, T4> Peg<R> sequence(
			Peg<T1> p1,
			Peg<T2> p2,
			Peg<T3> p3,
			Peg<T4> p4,
			Function4<? super T1, ? super T2, ? super T3, ? super T4, ? extends R> combiner) {
		return new Peg<>() {
			@Override
			public R parse(Tokenized src) {
				T1 r1 = p1.parse(src);
				if(r1 == null) {
					return null;
				}
				T2 r2 = p2.parse(src);
				if(r2 == null) {
					return null;
				}
				T3 r3 = p3.parse(src);
				if(r3 == null) {
					return null;
				}
				T4 r4 = p4.parse(src);
				if(r4 == null) {
					return null;
				}
				return combiner.apply(r1, r2, r3, r4);
			}
		};
	}

	public static <R, T1, T2, T3, T4, T5> Peg<R> sequence(
			Peg<T1> p1,
			Peg<T2> p2,
			Peg<T3> p3,
			Peg<T4> p4,
			Peg<T5> p5,
			Function5<? super T1, ? super T2, ? super T3, ? super T4, ? super T5, ? extends R> combiner) {
		return new Peg<>() {
			@Override
			public R parse(Tokenized src) {
				T1 r1 = p1.parse(src);
				if(r1 == null) {
					return null;
				}
				T2 r2 = p2.parse(src);
				if(r2 == null) {
					return null;
				}
				T3 r3 = p3.parse(src);
				if(r3 == null) {
					return null;
				}
				T4 r4 = p4.parse(src);
				if(r4 == null) {
					return null;
				}
				T5 r5 = p5.parse(src);
				if(r5 == null) {
					return null;
				}
				return combiner.apply(r1, r2, r3, r4, r5);
			}
		};
	}

	public static <R, T1, T2, T3, T4, T5, T6> Peg<R> sequence(
			Peg<T1> p1,
			Peg<T2> p2,
			Peg<T3> p3,
			Peg<T4> p4,
			Peg<T5> p5,
			Peg<T6> p6,
			Function6<? super T1, ? super T2, ? super T3, ? super T4, ? super T5, ? super T6, ? extends R> combiner) {
		return new Peg<>() {
			@Override
			public R parse(Tokenized src) {
				T1 r1 = p1.parse(src);
				if(r1 == null) {
					return null;
				}
				T2 r2 = p2.parse(src);
				if(r2 == null) {
					return null;
				}
				T3 r3 = p3.parse(src);
				if(r3 == null) {
					return null;
				}
				T4 r4 = p4.parse(src);
				if(r4 == null) {
					return null;
				}
				T5 r5 = p5.parse(src);
				if(r5 == null) {
					return null;
				}
				T6 r6 = p6.parse(src);
				if(r6 == null) {
					return null;
				}
				return combiner.apply(r1, r2, r3, r4, r5, r6);
			}
		};
	}

	public static <R, T1, T2, T3, T4, T5, T6, T7> Peg<R> sequence(
			Peg<T1> p1,
			Peg<T2> p2,
			Peg<T3> p3,
			Peg<T4> p4,
			Peg<T5> p5,
			Peg<T6> p6,
			Peg<T7> p7,
			Function7<? super T1, ? super T2, ? super T3, ? super T4, ? super T5, ? super T6, ? super T7, ? extends R> combiner) {
		return new Peg<>() {
			@Override
			public R parse(Tokenized src) {
				T1 r1 = p1.parse(src);
				if(r1 == null) {
					return null;
				}
				T2 r2 = p2.parse(src);
				if(r2 == null) {
					return null;
				}
				T3 r3 = p3.parse(src);
				if(r3 == null) {
					return null;
				}
				T4 r4 = p4.parse(src);
				if(r4 == null) {
					return null;
				}
				T5 r5 = p5.parse(src);
				if(r5 == null) {
					return null;
				}
				T6 r6 = p6.parse(src);
				if(r6 == null) {
					return null;
				}
				T7 r7 = p7.parse(src);
				if(r7 == null) {
					return null;
				}
				return combiner.apply(r1, r2, r3, r4, r5, r6, r7);
			}
		};
	}
}

@FunctionalInterface
interface Function3<T1, T2, T3, R> {
	R apply(T1 t1, T2 t2, T3 t3);
}

@FunctionalInterface
interface Function4<T1, T2, T3, T4, R> {
	R apply(T1 t1, T2 t2, T3 t3, T4 t4);
}

@FunctionalInterface
interface Function5<T1, T2, T3, T4, T5, R> {
	R apply(T1 t1, T2 t2, T3 t3, T4 t4, T5 t5);
}

@FunctionalInterface
interface Function6<T1, T2, T3, T4, T5, T6, R> {
	R apply(T1 t1, T2 t2, T3 t3, T4 t4, T5 t5, T6 t6);
}

@FunctionalInterface
interface Function7<T1, T2, T3, T4, T5, T6, T7, R> {
	R apply(T1 t1, T2 t2, T3 t3, T4 t4, T5 t5, T6 t6, T7 t7);
}