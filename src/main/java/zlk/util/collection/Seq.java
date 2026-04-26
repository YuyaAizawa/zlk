package zlk.util.collection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

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
/// 実装の本体はsliceとfoldIndexed．
/// foldやforEachなどはdefault実装を生成しているが，充分に最適化されないかもしれない．
///
/// @param <E>

public sealed interface Seq<E> extends Iterable<E> {

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

	int size();

	default boolean isEmpty() {
		return size() == 0;
	}

	boolean contains(E element);

	E at(int index);

	default E head() {
		return at(0);
	}

	default E last() {
		return at(size() - 1);
	}

	/**
	 * このSeqの部分Seqを返す
	 * @param from
	 * @param to
	 * @return
	 * @throws IllegalRangeException
	 */
	Seq<E> slice(int from, int to);


	/// 先頭から指定した要素数のSeqを返す．
	/// 足りなければあるだけを返す．
	/// @param num 先頭の要素数
	/// @return
	default Seq<E> take(int num) {
		if(num < 0) {
			throw new IllegalArgumentException();
		}
		num = Math.min(size(), num);

		return slice(0, num);
	}

	default Seq<E> dropLast() {
		if(isEmpty()) {
			throw new NoSuchElementException();
		}
		return take(size() - 1);
	}

	/// 先頭から指定した要素数を除いたSeqを返す．
	/// 足りなければ空のリストを返す．
	/// @param num
	/// @return
	default Seq<E> drop(int num) {
		if(num < 0) {
			throw new IllegalArgumentException();
		}
		num = Math.min(size(), num);

		return slice(num, size());
	}

	default Seq<E> tail() {
		if(isEmpty()) {
			throw new NoSuchElementException();
		}
		return drop(1);
	}

	/**
	 * 要素の順番を逆転させたSeqを返す．
	 * @return
	 */
	Seq<E> reversed();

	/**
	 * 畳み込みを行う
	 * @param <A> 畳み込み結果の型
	 * @param <R> 最終結果の型
	 * @param accumulator 畳み込みのステップ関数
	 * @param initialValue 畳み込みの初期値
	 * @param finisher 畳み込んだ結果から最終結果への変換
	 * @return 最終結果
	 */
	<A, R> R foldIndexed(BiFunctionIndexed<? super E, A, A> accumulator, A initialValue, Function<? super A, ? extends R> finisher);
	default <A, R> R foldIndexed(FolderIndexed<E, A, R> folder) {
		return foldIndexed(folder.accumulator(), folder.initialValue(), folder.finisher());
	}
	public interface FolderIndexed<E, A, R> {
		BiFunctionIndexed<? super E, A, A> accumulator();
		A initialValue();
		Function<? super A, ? extends R> finisher();
	}

	default <A, R> R fold(BiFunction<? super E, A, A> accumulator, A initialValue, Function<? super A, ? extends R> finisher) {
		return foldIndexed((_, e, acc) -> accumulator.apply(e, acc), initialValue, finisher);
	}
	default <A, R> R fold(Folder<E, A, R> folder) {
		return fold(folder.accumulator(), folder.initialValue(), folder.finisher());
	}
	public interface Folder<E, A, R> {
		BiFunction<? super E, A, A> accumulator();
		A initialValue();
		Function<? super A, ? extends R> finisher();
	}

	default <R> Seq<R> mapIndexed(FunctionIndexed<? super E, ? extends R> mapper) {
		return foldIndexed(
				(i, e, arr) -> { arr[i] = mapper.apply(i, e); return arr; },
				new Object[size()],
				ArraySeq::new);
	}

	default <R> Seq<R> map(Function<? super E, ? extends R> mapper) {
		return mapIndexed((_, e) -> mapper.apply(e));
	}

	default IntSeq mapToInt(ToIntFunction<? super E> mapper) {
		return foldIndexed(
				(i, e, arr) -> { arr[i] = mapper.applyAsInt(e); return arr; },
				new int[size()],
				IntArraySeq::new);
	}

	// TODO: 虚無のaccumulatorを入れた以下のdefault実装は充分に最適化されないかもしれない
	default Seq<E> filter(Predicate<? super E> predicate) {
		return fold(
				(e, stack) -> { if(predicate.test(e) ) { stack.add(e); } return stack; },
				new SeqBuffer<E>(size()),
				SeqBuffer::toSeq);
	}

	default void forEachIndexed(ConsumerIndexed<? super E> action) {
		foldIndexed(
				(i, e, _) -> { action.accept(i, e); return null; },
				null,
				Function.identity());
	}

	@Override
	default void forEach(Consumer<? super E> action) {
		forEachIndexed((_, e) -> action.accept(e));
	}

	default boolean equals(Seq<?> other) {
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

	default String join(CharSequence delimiter) {
		if(isEmpty()) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		Iterator<E> itr = iterator();
		sb.append(itr.next());
		itr.forEachRemaining(e -> {
			sb.append(delimiter).append(e);
		});
		return sb.toString();
	}

	default boolean anyMatch(Predicate<? super E> predicate) {
		if(isEmpty()) {
			return false;
		}
		for(E e : this) {
			if(predicate.test(e)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 移行のために用意した
	 * @return
	 */
	@Deprecated
	default List<E> toList() {
		List<E> result = new ArrayList<>(size());
		forEach(result::add);
		return result;
	}

	default <K, V> Map<K, V> toMap(
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
}

final class EmptySeq<E> implements Seq<E> {
	static final EmptySeq<?> INSTANCE = new EmptySeq<>();
	private EmptySeq() {}

	@Override
	public int size() { return 0; }
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
	public Seq<E> reversed() { return this; };
	@Override
	public <A, R> R foldIndexed(BiFunctionIndexed<? super E, A, A> accumulator, A initialValue, Function<? super A, ? extends R> finisher) {
		return finisher.apply(initialValue);
	}
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
}

final class SingletonSeq<E> implements Seq<E> {

	final E element;

	public SingletonSeq(E element) {
		this.element = element;
	}

	@Override
	public int size() {
		return 1;
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
}

final class ArraySeq<E> implements Seq<E> {

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
	@SuppressWarnings("unchecked")
	public E at(int index) {
		return (E) data[index];
	}

	@Override
	public boolean contains(E element) {
		if(element == null) {
			for (int i = 0; i < data.length; i++) {
				if(data[i] == null) {
					return true;
				}
			}
		} else {
			for (int i = 0; i < data.length; i++) {
				if(element.equals(data[i])) {
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public Seq<E> slice(int from, int to) {
		if(from < 0 || size() < to || to < from) {
			throw new IllegalRangeException(from, to, size());
		}
		if(from == to) {
			return Seq.of();
		}
		return new SliceSeq<>(data, from, to);
	}

	@Override
	public Seq<E> reversed() {
		return new SliceSeq<E>(data, 0, size()).reversed();
	}

	@Override
	@SuppressWarnings("unchecked")
	public <A, R> R foldIndexed(
			BiFunctionIndexed<? super E, A, A> folder,
			A initialValue,
			Function<? super A, ? extends R> finisher
	) {
		A acc = initialValue;
		for (int i = 0; i < size(); i++) {
			acc = folder.accumulate(i, (E) data[i], acc);
		}
		return finisher.apply(acc);
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
}

final class SliceSeq<E> implements Seq<E> {

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
	@SuppressWarnings("unchecked")
	public E at(int index) {
		if(index < 0 || size() <= index) {
			throw new ArrayIndexOutOfBoundsException(index);
		}
		return (E) ref[from + index];
	}

	@Override
	public boolean contains(E element) {
		if(element == null) {
			for (int i = from; i < to; i++) {
				if(ref[i] == null) {
					return true;
				}
			}
		} else {
			for (int i = from; i < to; i++) {
				if(element.equals(ref[i])) {
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public Seq<E> slice(int from, int to) {
		if(from < 0 || size() < to || to < from) {
			throw new IllegalRangeException(from, to, size());
		}
		if(from == to) {
			return Seq.of();
		}
		return new SliceSeq<>(ref, this.from + from, this.from + to);
	}

	@Override
	public Seq<E> reversed() {
		return new ReversedSeq<>(this);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <A, R> R foldIndexed(
			BiFunctionIndexed<? super E, A, A> folder,
			A initialValue,
			Function<? super A, ? extends R> finisher
	) {
		A acc = initialValue;
		for (int ni = 0, ri = from; ri < to; ni++, ri++) {
			acc = folder.accumulate(ni, (E) ref[ri], acc);
		}
		return finisher.apply(acc);
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
}

final class ReversedSeq<E> implements Seq<E> {

	final SliceSeq<E> ref;

	public ReversedSeq(SliceSeq<E> ref) {
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
	public boolean contains(E element) {
		return ref.contains(element);
	}

	@Override
	public E at(int index) {
		return ref.at(rel(index));
	}

	@Override
	public Seq<E> slice(int from, int to) {
		if (from < 0 || size() < to || to < from) {
			throw new IllegalRangeException(from, to, size());
		}
		if (from == to) {
			return Seq.of();
		}
		return ref.slice(size() - to, size() - from).reversed();
	}

	@Override
	public Seq<E> reversed() {
		return ref;
	}

	@Override
	@SuppressWarnings("unchecked")
	public <A, R> R foldIndexed(
			BiFunctionIndexed<? super E, A, A> folder,
			A initialValue,
			Function<? super A, ? extends R> finisher
	) {
		A acc = initialValue;
		for (int ni = 0, ri = abs(0); ri > abs(size()); ni++, ri--) {
			acc = folder.accumulate(ni, (E) ref.ref[ri], acc);
		}
		return finisher.apply(acc);
	}

	@Override
	public Iterator<E> iterator() {
		return new Iterator<>() {
			int index = abs(0);

			@Override
			public boolean hasNext() {
				return index > abs(size());
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
}

class IllegalRangeException extends IllegalArgumentException {
	private static final long serialVersionUID = 1L;

	public IllegalRangeException(int from, int to, int size) {
		super("from: " + from + ", to: " + to + ", size: " + size);
	}
}
