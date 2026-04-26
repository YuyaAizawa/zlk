package zlk.util.collection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.OptionalInt;
import java.util.PrimitiveIterator;
import java.util.function.Function;
import java.util.function.IntBinaryOperator;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;

import zlk.util.IntConsumerIndexed;
import zlk.util.IntFunctionIndexed;

/// {@link Seq}のint特殊化版

public sealed interface IntSeq extends Iterable<Integer> {

	public static IntSeq of() {
		return IntEmptySeq.SINGLETON;
	}

	public static IntSeq of(int...array) {
		switch(array.length) {
		case 0:
			return IntEmptySeq.SINGLETON;
		case 1:
			return new IntSingletonSeq(array[0]);
		default:
			return new IntArraySeq(Arrays.copyOf(array, array.length));
		}
	}

	@SafeVarargs
	public static IntSeq concat(IntSeq...args) {
		int totalSize = 0;
		for (int i = 0; i < args.length; i++) {
			totalSize += args[i].size();
		}

		int[] result = new int[totalSize];
		int count = 0;
		for (int i = 0; i < args.length; i++) {
			switch(args[i]) {
			case IntEmptySeq _:
				break;
			case IntSingletonSeq s:
				result[count] = s.element;
				break;
			case IntArraySeq a:
				System.arraycopy(a.data, 0, result, count, a.size());
				break;
			case IntSliceSeq s:
				System.arraycopy(s.ref, s.from, result, count, s.size());
				break;
			case IntReversedSeq r: {
				int count_ = count;
				r.forEachIndexed((j, e) -> result[count_+j] = e);
			}
			}
			count += args[i].size();
		}
		return new IntArraySeq(result);
	}

	int size();

	default boolean isEmpty() {
		return size() == 0;
	}

	boolean contains(int element);

	int at(int index);

	default int head() {
		return at(0);
	}

	default int last() {
		return at(size() - 1);
	}

	/**
	 * このSeqの部分Seqを返す
	 * @param from
	 * @param to
	 * @return
	 * @throws IllegalRangeException
	 */
	IntSeq slice(int from, int to);


	/// 先頭から指定した要素数のSeqを返す．
	/// 足りなければあるだけを返す．
	/// @param num 先頭の要素数
	/// @return
	default IntSeq take(int num) {
		if(num < 0) {
			throw new IllegalArgumentException();
		}
		num = Math.min(size(), num);

		return slice(0, num);
	}

	default IntSeq dropLast() {
		if(isEmpty()) {
			throw new NoSuchElementException();
		}
		return take(size() - 1);
	}

	/// 先頭から指定した要素数を除いたSeqを返す．
	/// 足りなければ空のリストを返す．
	/// @param num
	/// @return
	default IntSeq drop(int num) {
		if(num < 0) {
			throw new IllegalArgumentException();
		}
		num = Math.min(size(), num);

		return slice(num, size());
	}

	default IntSeq tail() {
		if(isEmpty()) {
			throw new NoSuchElementException();
		}
		return drop(1);
	}

	/**
	 * 要素の順番を逆転させたSeqを返す．
	 * @return
	 */
	IntSeq reversed();

	/**
	 * 畳み込みを行う
	 * @param <A> 畳み込み結果の型
	 * @param <R> 最終結果の型
	 * @param accumulator 畳み込みのステップ関数
	 * @param initialValue 畳み込みの初期値
	 * @param finisher 畳み込んだ結果から最終結果への変換
	 * @return 最終結果
	 */
	<A, R> R foldIndexed(AccumulatorIndexed<A> accumulator, A initialValue, Function<? super A, ? extends R> finisher);
	default <A, R> R foldIndexed(FolderIndexed<A, R> folder) {
		return foldIndexed(folder.accumulator(), folder.initialValue(), folder.finisher());
	}
	@FunctionalInterface
	public interface AccumulatorIndexed<A> {
		A accumulate(int index, int value, A acc);
	}
	public interface FolderIndexed<A, R> {
		AccumulatorIndexed<A> accumulator();
		A initialValue();
		Function<? super A, ? extends R> finisher();
	}

	default <A, R> R fold(Accumulator<A> accumulator, A initialValue, Function<? super A, ? extends R> finisher) {
		return foldIndexed((_, e, acc) -> accumulator.apply(e, acc), initialValue, finisher);
	}
	default <A, R> R fold(Folder<A, R> folder) {
		return fold(folder.accumulator(), folder.initialValue(), folder.finisher());
	}
	@FunctionalInterface
	public interface Accumulator<A> {
		A apply(int value, A acc);
	}
	public interface Folder<A, R> {
		Accumulator<A> accumulator();
		A initialValue();
		Function<? super A, ? extends R> finisher();
	}

	default <R> Seq<R> mapToObjIndexed(IntFunctionIndexed<? extends R> mapper) {
		return foldIndexed(
				(i, e, arr) -> { arr[i] = mapper.apply(i, e); return arr; },
				new Object[size()],
				ArraySeq::new);
	}

	default <R> Seq<R> mapToObj(IntFunction<? extends R> mapper) {
		return mapToObjIndexed((_, e) -> mapper.apply(e));
	}

	default IntSeq mapIndexed(IntBinaryOperator mapper) {
		return foldIndexed(
				(int i, int e, int[] arr) -> { arr[i] = mapper.applyAsInt(i, e); return arr; },
				new int[size()],
				IntArraySeq::new);
	}

	default IntSeq map(IntUnaryOperator mapper) {
		return mapIndexed((_, e) -> mapper.applyAsInt(e));
	}

	default IntSeq filter(IntPredicate predicate) {
		return fold(
				(e, stack) -> { if(predicate.test(e) ) { stack.add(e); } return stack; },
				new IntSeqBuffer(size()),
				IntSeqBuffer::toSeq);
	}

	default void forEachIndexed(IntConsumerIndexed action) {
		foldIndexed(
				(i, e, _) -> { action.accept(i, e); return null; },
				null,
				Function.identity());
	}

	default void forEach(IntConsumer action) {
		forEachIndexed((_, e) -> action.accept(e));
	}

	default OptionalInt max() {
		if(isEmpty()) {
			return OptionalInt.empty();
		}
		int max = head();
		for(int i : tail()) {
			if(i > max) {
				max = i;
			}
		}
		return OptionalInt.of(max);
	}

	/**
	 * 移行のために用意した
	 * @return
	 */
	@Deprecated
	default List<Integer> toList() {
		List<Integer> result = new ArrayList<>(size());
		forEach((IntConsumer) result::add);
		return result;
	}
}

enum IntEmptySeq implements IntSeq {
	SINGLETON;

	@Override
	public int size() { return 0; }
	@Override
	public boolean contains(int element) { return false; }
	@Override
	public int at(int index) { throw new ArrayIndexOutOfBoundsException(index); }
	@Override
	public IntSeq slice(int from, int to) {
		if(from == 0 && to == 0) {
			return this;
		}
		throw new IllegalRangeException(from, to, size());
	}
	@Override
	public IntSeq reversed() { return this; };
	@Override
	public <A, R> R foldIndexed(AccumulatorIndexed<A> accumulator, A initialValue, Function<? super A, ? extends R> finisher) {
		return finisher.apply(initialValue);
	}
	@Override
	public PrimitiveIterator.OfInt iterator() {
		return new PrimitiveIterator.OfInt() {
			@Override
			public boolean hasNext() { return false; }
			@Override
			public int nextInt() { throw new NoSuchElementException(); }
		};
	}
}

final class IntSingletonSeq implements IntSeq {

	final int element;

	public IntSingletonSeq(int element) {
		this.element = element;
	}

	@Override
	public int size() {
		return 1;
	}

	@Override
	public boolean contains(int element) {
		return element == this.element;
	}

	@Override
	public int at(int index) {
		if(index != 0) {
			throw new ArrayIndexOutOfBoundsException(index);
		}
		return element;
	}

	@Override
	public IntSeq slice(int from, int to) {
		if(from < 0 || size() < to || to < from) {
			throw new IllegalRangeException(from, to, size());
		}
		if(from == to) {
			return IntSeq.of();
		}
		return this;  // from == 0 && to == 1
	}

	@Override
	public IntSeq reversed() {
		return this;
	}

	@Override
	public <A, R> R foldIndexed(
			AccumulatorIndexed<A> accumulator,
			A initialValue,
			Function<? super A, ? extends R> finisher
	) {
		A acc = accumulator.accumulate(0, element, initialValue);
		return finisher.apply(acc);
	}

	@Override
	public PrimitiveIterator.OfInt iterator() {
		return new PrimitiveIterator.OfInt() {
			boolean consumed = false;

			@Override
			public boolean hasNext() {
				return !consumed;
			}

			@Override
			public int nextInt() {
				if(consumed) {
					throw new NoSuchElementException();
				}
				consumed = true;
				return element;
			}

		};
	}
}

final class IntArraySeq implements IntSeq {

	final int[] data;

	/**
	 * @param data 外部から変更されないことを保証せよ
	 */
	IntArraySeq(int[] data) {
		this.data = data;
	}

	@Override
	public int size() {
		return data.length;
	}

	@Override
	public int at(int index) {
		return data[index];
	}

	@Override
	public boolean contains(int element) {
		for (int i = 0; i < data.length; i++) {
			if(element == data[i]) {
				return true;
			}
		}
		return false;
	}

	@Override
	public IntSeq slice(int from, int to) {
		if(from < 0 || size() < to || to < from) {
			throw new IllegalRangeException(from, to, size());
		}
		if(from == to) {
			return IntSeq.of();
		}
		return new IntSliceSeq(data, from, to);
	}

	@Override
	public IntSeq reversed() {
		return new IntSliceSeq(data, 0, size()).reversed();
	}

	@Override
	public <A, R> R foldIndexed(
			AccumulatorIndexed<A> folder,
			A initialValue,
			Function<? super A, ? extends R> finisher
	) {
		A acc = initialValue;
		for (int i = 0; i < size(); i++) {
			acc = folder.accumulate(i, data[i], acc);
		}
		return finisher.apply(acc);
	}

	@Override
	public PrimitiveIterator.OfInt iterator() {
		return new PrimitiveIterator.OfInt() {
			int index = 0;

			@Override
			public boolean hasNext() {
				return index < data.length;
			}

			@Override
			public int nextInt() {
				if(!hasNext()) {
					throw new NoSuchElementException();
				}
				return data[index++];
			}

			@Override
			public void forEachRemaining(IntConsumer action) {
				for (int i = index; i < data.length; i++) {
					action.accept(data[i]);
				}
			}
		};
	}
}

final class IntSliceSeq implements IntSeq {

	final int[] ref;
	final int from;
	final int to;

	public IntSliceSeq(int[] ref, int from, int to) {
		this.ref = ref;
		this.from = from;
		this.to = to;
	}

	@Override
	public int size() {
		return to - from;
	}

	@Override
	public int at(int index) {
		if(index < 0 || size() <= index) {
			throw new ArrayIndexOutOfBoundsException(index);
		}
		return ref[from + index];
	}

	@Override
	public boolean contains(int element) {
		for (int i = from; i < to; i++) {
			if(element == ref[i]) {
				return true;
			}
		}
		return false;
	}

	@Override
	public IntSeq slice(int from, int to) {
		if(from < 0 || size() < to || to < from) {
			throw new IllegalRangeException(from, to, size());
		}
		if(from == to) {
			return IntSeq.of();
		}
		return new IntSliceSeq(ref, this.from + from, this.from + to);
	}

	@Override
	public IntSeq reversed() {
		return new IntReversedSeq(this);
	}

	@Override
	public <A, R> R foldIndexed(
			AccumulatorIndexed<A> folder,
			A initialValue,
			Function<? super A, ? extends R> finisher
	) {
		A acc = initialValue;
		for (int ni = 0, ri = from; ri < to; ni++, ri++) {
			acc = folder.accumulate(ni, ref[ri], acc);
		}
		return finisher.apply(acc);
	}

	@Override
	public PrimitiveIterator.OfInt iterator() {
		return new PrimitiveIterator.OfInt() {
			int index = from;

			@Override
			public boolean hasNext() {
				return index < to;
			}

			@Override
			public int nextInt() {
				if(!hasNext()) {
					throw new NoSuchElementException();
				}
				return ref[index++];
			}

			@Override
			public void forEachRemaining(IntConsumer action) {
				for (int i = index; i < to; i++) {
					action.accept(ref[i]);
				}
			}
		};
	}
}

final class IntReversedSeq implements IntSeq {

	final IntSliceSeq ref;

	public IntReversedSeq(IntSliceSeq ref) {
		this.ref = ref;
	}

	@Override
	public int size() {
		return ref.size();
	}

	/**
	 * この反転スライスに対する添え字を，refのSlice上のものに変換する
	 * @param index
	 * @return
	 */
	private int rel(int index) {
		return size() - 1 - index;
	}

	/**
	 * この反転スライスに対する添え字を，refのref，配列上のインデックスに変換する
	 * @param index
	 * @return
	 */
	private int abs(int index) {
		return ref.from + rel(index);
	}

	@Override
	public boolean contains(int element) {
		return ref.contains(element);
	}

	@Override
	public int at(int index) {
		return ref.at(rel(index));
	}

	@Override
	public IntSeq slice(int from, int to) {
		if (from < 0 || size() < to || to < from) {
			throw new IllegalRangeException(from, to, size());
		}
		if (from == to) {
			return IntSeq.of();
		}
		return ref.slice(size() - to, size() - from).reversed();
	}

	@Override
	public IntSeq reversed() {
		return ref;
	}

	@Override
	public <A, R> R foldIndexed(
			AccumulatorIndexed<A> folder,
			A initialValue,
			Function<? super A, ? extends R> finisher
	) {
		A acc = initialValue;
		for (int ni = 0, ri = abs(0); ri > abs(size()); ni++, ri--) {
			acc = folder.accumulate(ni, ref.ref[ri], acc);
		}
		return finisher.apply(acc);
	}

	@Override
	public PrimitiveIterator.OfInt iterator() {
		return new PrimitiveIterator.OfInt() {
			int index = abs(0);

			@Override
			public boolean hasNext() {
				return index > abs(size());
			}

			@Override
			public int nextInt() {
				if(!hasNext()) {
					throw new NoSuchElementException();
				}
				return ref.ref[index--];
			}

			@Override
			public void forEachRemaining(IntConsumer action) {
				int limit = abs(size());
				for (int i = index; i > limit; i--) {
					action.accept(ref.ref[i]);
				}
			}
		};
	}
}
