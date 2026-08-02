package zlk.util.collection;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

import zlk.util.BiConsumerIndexed;
import zlk.util.BiFunctionIndexed;
import zlk.util.ConsumerIndexed;
import zlk.util.FunctionIndexed;

/// 不変リストデータ構造
///
/// 想定用途
/// - 不変データ型の内部
///
/// stream()と書くのが面倒なので直接Seqになるfilterやmapがある
///
/// @param <E>

public sealed abstract class Seq<E> implements Iterable<E> {

	@SuppressWarnings("unchecked")
	public static <E> Seq<E> of() {
		return (Seq<E>) EmptySeq.INSTANCE;
	}

	public static <E> Seq<E> of(E element) {
		return new SingletonSeq<>(element);
	}

	@SafeVarargs
	public static <E> Seq<E> of(E...array) {
		switch(array.length) {
		case 0:
			@SuppressWarnings("unchecked")
			var result = (Seq<E>) EmptySeq.INSTANCE;
			return result;
		case 1:
			return new SingletonSeq<>(array[0]);
		default:
			return new ArraySeq<>(Arrays.copyOf(array, array.length));
		}
	}

	public static <E> Seq<E> from(Collection<? extends E> original) {
		int size = original.size();

		if(size == 0) {
			@SuppressWarnings("unchecked")
			var result = (Seq<E>) EmptySeq.INSTANCE;
			return result;
		}

		Iterator<? extends E> itr = original.iterator();
		if(size == 1) {
			return new SingletonSeq<>(itr.next());
		}

		Object[] data = new Object[size];
		for(int i = 0; i < size; i++) {
			data[i] = itr.next();
		}
		return new ArraySeq<>(data);
	}

	public static <E> Seq<E> repeat(E element, int count) {
		if(count < 0) {
			throw new IllegalArgumentException("count: "+count);
		}
		switch(count) {
		case 0:
			return Seq.of();
		case 1:
			return Seq.of(element);
		case 2:
			return new ArraySeq<>(new Object[] {element, element});
		case 3:
			return new ArraySeq<>(new Object[] {element, element, element});
		case 4:
			return new ArraySeq<>(new Object[] {element, element, element, element});
		}
		Object[] data = new Object[count];
		Arrays.fill(data, element);
		return new ArraySeq<>(data);
	}

	@SafeVarargs
	public static <E> Seq<E> concat(Seq<? extends E>...args) {
		int totalSize = 0;
		for (int i = 0; i < args.length; i++) {
			totalSize += args[i].size();
		}

		Object[] result = new Object[totalSize];
		int count = 0;
		for (int i = 0; i < args.length; i++) {
			switch(args[i]) {
			case EmptySeq<? extends E> _:
				break;
			case SingletonSeq<? extends E> s:
				result[count] = s.element;
				break;
			case ArraySeq<? extends E> a:
				System.arraycopy(a.data, 0, result, count, a.size());
				break;
			case SliceSeq<? extends E> s:
				System.arraycopy(s.ref, s.from, result, count, s.size());
				break;
			case ReversedSeq<? extends E> r: {
				int count_ = count;
				r.forEachIndexed((j, e) -> result[count_+j] = e);
			}
			}
			count += args[i].size();
		}
		return new ArraySeq<>(result);
	}

	/**
	 * 大きさの等しい2つのSeqの同一のインデックスの要素に対する操作を提供する．
	 * @param <E>
	 * @param <F>
	 * @param left
	 * @param right
	 * @return 操作可能なオブジェクト
	 * @throws IllegalArgumentException 2つのSeqの大きさが異なる場合
	 */
	public static <E, F> Zip<E, F> zip(Seq<E> left, Seq<F> right) {
		return new Zip<>(left, right);
	}

	public abstract int size();

	public boolean isEmpty() {
		return size() == 0;
	}

	/**
	 * 実体の配列を返す．
	 *
	 * Seqの実体は連続した配列上に配置されており，forでの連続アクセスを想定している．
	 * @return
	 */
	abstract Object[] array();

	/**
	 * 指定されたindexに当たる実体の配列上のindexを返す．
	 *
	 * Seqの実体は連続した配列上に配置されており，forでの連続アクセスを想定している．
	 * このメソッドはインデックスの範囲を検査しない．
	 * @param idx
	 * @return
	 */
	abstract int absIdx(int idx);

	public boolean contains(E element) {
		Object[] data = array();

		int from = absIdx(0);
		int to = absIdx(size());
		if(from > to) {
			int tmp = from;
			from = to + 1;
			to = tmp + 1;
		}

		if(element == null) {
			for (int i = from; i < to; i++) {
				if(data[i] == null) {
					return true;
				}
			}
		} else {
			for (int i = from; i < to; i++) {
				if(element.equals(data[i])) {
					return true;
				}
			}
		}
		return false;
	}

	@SuppressWarnings("unchecked")
	public E at(int idx) {
		if(idx < 0 || size() <= idx) {
			throw new ArrayIndexOutOfBoundsException(idx);
		}
		return (E) array()[absIdx(idx)];
	}

	public E head() {
		return at(0);
	}

	public E last() {
		return at(size() - 1);
	}

	/**
	 * このSeqの部分Seqを返す
	 * @param from 開始インデックス(inclusive)
	 * @param to 終了インデックス(exclusive)
	 * @return
	 * @throws IllegalRangeException 範囲が不正な場合
	 */
	public Seq<E> slice(int from, int to) {
		if(from < 0 || size() < to || to < from) {
			throw new IllegalRangeException(from, to, size());
		}
		if(from == to) {
			return Seq.of();
		}
		if(from + 1 == to) {
			return new SingletonSeq<>(at(from));
		}
		if(from == 0 && to == size()) {
			return this;
		}

		// slice範囲の計算
		Object[] data = array();
		from = absIdx(from);
		to = absIdx(to);

		if(from < to) {
			return new SliceSeq<>(data, from, to);
		} else {
			// reversed
			return new SliceSeq<E>(data, to + 1, from + 1).reversed();
		}
	}

	/**
	 * 先頭から指定した要素数のSeqを返す．
	 * 足りなければあるだけを返す．
	 * @param num 先頭の要素数
	 * @return
	 */
	public Seq<E> take(int num) {
		if(num < 0) {
			throw new IllegalArgumentException();
		}
		num = Math.min(size(), num);

		return slice(0, num);
	}

	public Seq<E> dropLast() {
		if(isEmpty()) {
			throw new NoSuchElementException();
		}
		return take(size() - 1);
	}

	/**
	 * 先頭から指定した要素数を除いたSeqを返す．
	 * 足りなければ空のリストを返す．
	 * @param num
	 * @return
	 */
	public Seq<E> drop(int num) {
		if(num < 0) {
			throw new IllegalArgumentException();
		}
		num = Math.min(size(), num);

		return slice(num, size());
	}

	public Seq<E> tail() {
		if(isEmpty()) {
			throw new NoSuchElementException();
		}
		return drop(1);
	}

	/**
	 * 要素をソートしたSeqを返す．
	 * @param comparator
	 * @return
	 */
	public Seq<E> sorted(Comparator<E> comparator) {
		int from = absIdx(0);
		int to = absIdx(size());
		if(from > to) {
			int tmp = from;
			from = to + 1;
			to = tmp + 1;
			comparator = comparator.reversed();
		}

		@SuppressWarnings("unchecked")
		E[] elements = (E[]) Arrays.copyOfRange(array(), from, to);
		Arrays.sort(elements, comparator);
		return new ArraySeq<>(elements);
	}

	/**
	 * 要素の順番を逆転させたSeqを返す．
	 * @return
	 */
	public abstract Seq<E> reversed();

	/**
	 * 畳み込みを行う
	 * @param <A> 畳み込み結果の型
	 * @param <R> 最終結果の型
	 * @param accumulator 畳み込みのステップ関数
	 * @param initialValue 畳み込みの初期値
	 * @param finisher 畳み込んだ結果から最終結果への変換
	 * @return 最終結果
	 */
	@SuppressWarnings("unchecked")
	public <A, R> R foldIndexed(BiFunctionIndexed<? super E, A, A> accumulator, A initialValue, Function<? super A, ? extends R> finisher) {
		A result = initialValue;

		Object[] data = array();
		int from = absIdx(0);
		int to = absIdx(size());
		if(from <= to) {
			for(int idx = 0, jdx = from; jdx < to; idx++, jdx++) {
				result = accumulator.accumulate(idx, (E) data[jdx], result);
			}
		} else {
			for(int idx = 0, jdx = from; to < jdx; idx++, jdx--) {
				result = accumulator.accumulate(idx, (E) data[jdx], result);
			}
		}
		return finisher.apply(result);
	}
	public <A, R> R foldIndexed(FolderIndexed<E, A, R> folder) {
		return foldIndexed(folder.accumulator(), folder.initialValue(), folder.finisher());
	}
	public interface FolderIndexed<E, A, R> {
		BiFunctionIndexed<? super E, A, A> accumulator();
		A initialValue();
		Function<? super A, ? extends R> finisher();
	}

	public <A, R> R fold(BiFunction<? super E, A, A> accumulator, A initialValue, Function<? super A, ? extends R> finisher) {
		return foldIndexed((_, e, acc) -> accumulator.apply(e, acc), initialValue, finisher);
	}
	public <A, R> R fold(Folder<E, A, R> folder) {
		return fold(folder.accumulator(), folder.initialValue(), folder.finisher());
	}
	public interface Folder<E, A, R> {
		BiFunction<? super E, A, A> accumulator();
		A initialValue();
		Function<? super A, ? extends R> finisher();
	}

	@SuppressWarnings("unchecked")
	public <R> Seq<R> mapIndexed(FunctionIndexed<? super E, ? extends R> mapper) {
		SeqBuffer<R> result = new SeqBuffer<>(size());

		Object[] data = array();
		int from = absIdx(0);
		int to = absIdx(size());
		if(from <= to) {
			for(int idx = 0, jdx = from; jdx < to; idx++, jdx++) {
				result.add(mapper.apply(idx, (E) data[jdx]));
			}
		} else {
			for(int idx = 0, jdx = from; to < jdx; idx++, jdx--) {
				result.add(mapper.apply(idx, (E) data[jdx]));
			}
		}

		return result.toSeq();
	}

	public <R> Seq<R> map(Function<? super E, ? extends R> mapper) {
		return mapIndexed((_, e) -> mapper.apply(e));
	}

	public IntSeq mapToInt(ToIntFunction<? super E> mapper) {
		return foldIndexed(
				(i, e, arr) -> { arr[i] = mapper.applyAsInt(e); return arr; },
				new int[size()],
				IntArraySeq::new);
	}

	@SuppressWarnings("unchecked")
	public Seq<E> filter(Predicate<? super E> predicate) {
		SeqBuffer<E> result = new SeqBuffer<>();

		Object[] data = array();
		int from = absIdx(0);
		int to = absIdx(size());
		if(from <= to) {
			for(int idx = from; idx < to; idx++) {
				E e = (E) data[idx];
				if(predicate.test(e)) {
					result.add(e);
				}
			}
		} else {
			for(int jdx = from; to < jdx; jdx--) {
				E e = (E) data[jdx];
				if(predicate.test(e)) {
					result.add(e);
				}
			}
		}
		return result.toSeq();
	}

	@SuppressWarnings("unchecked")
	public void forEachIndexed(ConsumerIndexed<? super E> action) {
		Object[] data = array();
		int from = absIdx(0);
		int to = absIdx(size());
		if(from <= to) {
			for(int idx = 0, jdx = from; jdx < to; idx++, jdx++) {
				action.accept(idx, (E) data[jdx]);
			}
		} else {
			for(int idx = 0, jdx = from; to < jdx; idx++, jdx--) {
				action.accept(idx, (E) data[jdx]);
			}
		}
	}

	@Override
	public void forEach(Consumer<? super E> action) {
		forEachIndexed((_, e) -> action.accept(e));
	}

	public boolean equals(Seq<?> other) {
		if(this == Objects.requireNonNull(other)) {
			return true;
		}
		if(this.size() != other.size()) {
			return false;
		}
		Iterator<E> thisItr = iterator();
		Iterator<?> otherItr = other.iterator();
		while(thisItr.hasNext()) {
			E thisElm = thisItr.next();
			if(thisElm == null && otherItr.next() != null) {
				return false;
			} else if(!thisElm.equals(otherItr.next())){
				return false;
			}
		}
		return true;
	}

	public String join(CharSequence delimiter) {
		switch(this) {
		case EmptySeq<E> _:
			return "";
		case SingletonSeq<E> s:
			return s.element.toString();
		default:
			break;
		}  // 以下は2要素以上

		StringBuilder sb = new StringBuilder();
		sb.append(head());
		tail().forEach(e -> {
			sb.append(delimiter).append(e);
		});
		return sb.toString();
	}

	@SuppressWarnings("unchecked")
	public Optional<E> findFirst(Predicate<? super E> predicate) {
		Object[] data = array();
		int from = absIdx(0);
		int to = absIdx(size());
		if(from <= to) {
			for(int idx = from; idx < to; idx++) {
				E e = (E) data[idx];
				if(predicate.test(e)) {
					return Optional.of(e);
				}
			}
		} else {
			for(int jdx = from; to < jdx; jdx--) {
				E e = (E) data[jdx];
				if(predicate.test(e)) {
					return Optional.of(e);
				}
			}
		}
		return Optional.empty();
	}

	public boolean anyMatch(Predicate<? super E> predicate) {
		return findFirst(predicate).isPresent();
	}

	public boolean allMatch(Predicate<? super E> predicate) {
		return findFirst(predicate.negate()).isEmpty();
	}

	public <K, V> Map<K, V> toMap(
			Function<? super E, ? extends K> keyExtractor,
			Function<? super E, ? extends V> valueExtractor
	) {
		return fold(new Seq.Folder<E, Map<K, V>, Map<K, V>>() {

			@Override
			public BiFunction<? super E, Map<K, V>, Map<K, V>> accumulator() {
				return (e, idMap) -> { idMap.put(keyExtractor.apply(e), valueExtractor.apply(e)); return idMap; };
			}

			@Override
			public Map<K, V> initialValue() {
				return new HashMap<>();
			}

			@Override
			public Function<? super Map<K, V>, ? extends Map<K, V>> finisher() {
				return Function.identity();
			}
		});
	}

	public static final class Zip<E, F> {
		private final Seq<E> left;
		private final Seq<F> right;

		private Zip(Seq<E> left, Seq<F> right) {
			if(left.size() != right.size()) {
				throw new IllegalArgumentException(String.format(
						"size unmatch, left: %d, right: %d", left.size(), right.size()));
			}
			this.left = left;
			this.right = right;
		}

		public <R> Seq<R> map(BiFunction<? super E, ? super F, ? extends R> mapper) {
			int size = left.size();
			switch(size) {
			case 0:
				return Seq.of();
			case 1:
				return Seq.of(mapper.apply(left.head(), right.head()));
			}
			SeqBuffer<R> result = new SeqBuffer<>(size);
			Iterator<E> li = left.iterator();
			Iterator<F> ri = right.iterator();
			while(li.hasNext()) {
				result.add(mapper.apply(li.next(), ri.next()));
			}
			return result.toSeq();
		}

		public void forEachIndexed(BiConsumerIndexed<? super E, ? super F> action) {
			int size = left.size();
			switch(size) {
			case 0:
				return;
			case 1:
				action.accept(0, left.head(), right.head());
				return;
			}
			Iterator<E> li = left.iterator();
			Iterator<F> ri = right.iterator();
			for(int idx = 0; idx < size; idx++) {
				action.accept(idx, li.next(), ri.next());
			}
		}

		public void forEach(BiConsumer<? super E, ? super F> action) {
			Iterator<E> li = left.iterator();
			Iterator<F> ri = right.iterator();
			while(li.hasNext()) {
				action.accept(li.next(), ri.next());
			}
		}
	}
}

final class EmptySeq<E> extends Seq<E> {
	static final EmptySeq<?> INSTANCE = new EmptySeq<>();
	private EmptySeq() {}

	@Override
	public int size() { return 0; }
	@Override
	int absIdx(int idx) { return idx; }
	@Override
	Object[] array() { throw new IllegalStateException(); }
	@Override
	public boolean contains(E element) { return false; }
	@Override
	public E at(int index) { throw new ArrayIndexOutOfBoundsException(index); }
	@Override
	public Seq<E> slice(int from, int to) {
		if(from == 0 && to == 0) {
			return this;
		}
		throw new IllegalRangeException(from, to, size());
	}
	@Override
	public Seq<E> sorted(Comparator<E> comparator) { return this; }
	@Override
	public Seq<E> reversed() { return this; };
	@Override
	public <A, R> R foldIndexed(BiFunctionIndexed<? super E, A, A> accumulator, A initialValue, Function<? super A, ? extends R> finisher) {
		return finisher.apply(initialValue);
	}
	@Override
	public <R> Seq<R> mapIndexed(FunctionIndexed<? super E, ? extends R> mapper) { return Seq.of(); }
	@Override
	public Seq<E> filter(Predicate<? super E> predicate) { return this; }
	@Override
	public void forEachIndexed(ConsumerIndexed<? super E> action) {}
	@Override
	public Optional<E> findFirst(Predicate<? super E> predicate) { return Optional.empty(); }
	@Override
	public Iterator<E> iterator() {
		return new Iterator<>() {
			@Override
			public boolean hasNext() { return false; }
			@Override
			public E next() { throw new NoSuchElementException(); }
		};
	}

	@Override
	public boolean equals(Object obj) {
		if(obj instanceof Seq other) {
			return equals(other);
		}
		return false;
	}

	@Override
	public String toString() {
		return "[]";
	}
}

final class SingletonSeq<E> extends Seq<E> {

	final E element;

	public SingletonSeq(E element) {
		this.element = element;
	}

	@Override
	public int size() {
		return 1;
	}

	@Override
	int absIdx(int idx) {
		return idx;
	}

	@Override
	Object[] array() {
		throw new IllegalStateException();
	}

	@Override
	public boolean contains(E element) {
		if(element == null) {
			return this.element == null;
		} else {
			return element.equals(this.element);
		}
	}

	@Override
	public E at(int index) {
		if(index != 0) {
			throw new ArrayIndexOutOfBoundsException(index);
		}
		return element;
	}

	@Override
	public Seq<E> slice(int from, int to) {
		if(from < 0 || size() < to || to < from) {
			throw new IllegalRangeException(from, to, size());
		}
		if(from == to) {
			return Seq.of();
		}
		return this;  // from == 0 && to == 1
	}

	@Override
	public Seq<E> sorted(Comparator<E> comparator) {
		return this;
	}

	@Override
	public Seq<E> reversed() {
		return this;
	}

	@Override
	public <A, R> R foldIndexed(
			BiFunctionIndexed<? super E, A, A> accumulator,
			A initialValue,
			Function<? super A, ? extends R> finisher
	) {
		A acc = accumulator.accumulate(0, element, initialValue);
		return finisher.apply(acc);
	}

	@Override
	public <R> Seq<R> mapIndexed(FunctionIndexed<? super E, ? extends R> mapper) {
		return Seq.of(mapper.apply(0, element));
	}

	@Override
	public Seq<E> filter(Predicate<? super E> predicate) {
		if(predicate.test(element)) {
			return this;
		}
		return Seq.of();
	}

	@Override
	public void forEachIndexed(ConsumerIndexed<? super E> action) {
		action.accept(0, element);
	}

	@Override
	public Optional<E> findFirst(Predicate<? super E> predicate) {
		if(predicate.test(element)) {
			return Optional.of(element);
		}
		return Optional.empty();
	}

	@Override
	public Iterator<E> iterator() {
		return new Iterator<>() {
			boolean consumed = false;

			@Override
			public boolean hasNext() {
				return !consumed;
			}

			@Override
			public E next() {
				if(consumed) {
					throw new NoSuchElementException();
				}
				consumed = true;
				return element;
			}

		};
	}

	@Override
	public boolean equals(Object obj) {
		if(obj instanceof Seq other) {
			return equals(other);
		}
		return false;
	}

	@Override
	public String toString() {
		return "[" + element + "]";
	}
}

final class ArraySeq<E> extends Seq<E> {

	final Object[] data;

	/**
	 * @param data 外部から変更されないことを保証せよ
	 */
	ArraySeq(Object[] data) {
		this.data = data;
	}

	@Override
	public int size() {
		return data.length;
	}

	@Override
	Object[] array() {
		return data;
	}

	@Override
	int absIdx(int idx) {
		return idx;
	}

	@Override
	public Seq<E> reversed() {
		return new SliceSeq<E>(data, 0, size()).reversed();
	}

	@Override
	public Iterator<E> iterator() {
		return new Iterator<>() {
			int index = 0;

			@Override
			public boolean hasNext() {
				return index < size();
			}

			@Override
			@SuppressWarnings("unchecked")
			public E next() {
				if(!hasNext()) {
					throw new NoSuchElementException();
				}
				return (E) data[index++];
			}
		};
	}

	@Override
	public boolean equals(Object obj) {
		if(obj instanceof Seq other) {
			return equals(other);
		}
		return false;
	}

	@Override
	public String toString() {
		return "[" + join(", ") + "]";
	}
}

final class SliceSeq<E> extends Seq<E> {

	final Object[] ref;
	final int from;
	final int to;

	public SliceSeq(Object[] ref, int from, int to) {
		this.ref = ref;
		this.from = from;
		this.to = to;
	}

	@Override
	public int size() {
		return to - from;
	}

	@Override
	Object[] array() {
		return ref;
	}

	@Override
	int absIdx(int idx) {
		return idx + from;
	}

	@Override
	public Seq<E> reversed() {
		return new ReversedSeq<>(this);
	}

	@Override
	public Iterator<E> iterator() {
		return new Iterator<>() {
			int index = from;

			@Override
			public boolean hasNext() {
				return index < to;
			}

			@Override
			@SuppressWarnings("unchecked")
			public E next() {
				if(!hasNext()) {
					throw new NoSuchElementException();
				}
				return (E) ref[index++];
			}
		};
	}

	@Override
	public boolean equals(Object obj) {
		if(obj instanceof Seq other) {
			return equals(other);
		}
		return false;
	}

	@Override
	public String toString() {
		return "[" + join(", ") + "]";
	}
}

final class ReversedSeq<E> extends Seq<E> {

	final SliceSeq<E> ref;

	public ReversedSeq(SliceSeq<E> ref) {
		this.ref = ref;
	}

	@Override
	public int size() {
		return ref.size();
	}

	@Override
	Object[] array() {
		return ref.array();
	}

	@Override
	int absIdx(int idx) {
		return ref.from - idx + size() - 1 ;
	}

	@Override
	public Seq<E> reversed() {
		return ref;
	}

	@Override
	public Iterator<E> iterator() {
		return new Iterator<>() {
			int index = absIdx(0);

			@Override
			public boolean hasNext() {
				return index > absIdx(size());
			}

			@Override
			@SuppressWarnings("unchecked")
			public E next() {
				if(!hasNext()) {
					throw new NoSuchElementException();
				}
				return (E) ref.ref[index--];
			}
		};
	}

	@Override
	public boolean equals(Object obj) {
		if(obj instanceof Seq other) {
			return equals(other);
		}
		return false;
	}

	@Override
	public String toString() {
		return "[" + join(", ") + "]";
	}
}

class IllegalRangeException extends IllegalArgumentException {
	private static final long serialVersionUID = 1L;

	public IllegalRangeException(int from, int to, int size) {
		super("from: " + from + ", to: " + to + ", size: " + size);
	}
}
