package zlk.util.collection;

import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import zlk.util.IndexedConsumer;

/// 不変リストデータ構造
///
/// 想定用途
/// - 不変データ型の内部
///
/// stream()と書くのが面倒なので直接Seqになるfilterやmapがある
///
/// @param <E>

public interface Seq<E> extends Iterable<E> {

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

	@SafeVarargs
	public static <E> Seq<E> concat(Seq<E>...args) {
		int totalSize = 0;
		for (int i = 0; i < args.length; i++) {
			totalSize += args[i].size();
		}

		Object[] result = new Object[totalSize];
		int count = 0;
		for (int i = 0; i < args.length; i++) {
			System.arraycopy(args[i], 0, result, count, args[i].size());
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

	default E first() {
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

	/**
	 * 要素の順番を逆転させたSeqを返す．
	 * @return
	 */
	Seq<E> reversed();
	Seq<E> filter(Predicate<? super E> predicate);
	<R> Seq<R> map(Function<? super E, ? extends R> mapper);
	@Override
	void forEach(Consumer<? super E> action);
	void forEachIndexed(IndexedConsumer<? super E> action);
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
	public Iterator<E> iterator() {
		return new Iterator<>() {
			@Override
			public boolean hasNext() { return false; }
			@Override
			public E next() { throw new NoSuchElementException(); }
		};
	}
	@Override
	public Seq<E> filter(Predicate<? super E> predicate) { return Seq.of(); }
	@Override
	public <R> Seq<R> map(Function<? super E, ? extends R> mapper) { return Seq.of(); }
	@Override
	public void forEach(Consumer<? super E> action) {}
	@Override
	public void forEachIndexed(IndexedConsumer<? super E> action) {}
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
	public Seq<E> filter(Predicate<? super E> predicate) {
		if(predicate.test(element)) {
			return this;
		} else {
			return Seq.of();
		}
	}

	@Override
	public <R> Seq<R> map(Function<? super E, ? extends R> mapper) {
		return Seq.of(mapper.apply(element));
	}

	@Override
	public void forEach(Consumer<? super E> action) {
		action.accept(element);
	}

	@Override
	public void forEachIndexed(IndexedConsumer<? super E> action) {
		action.accept(0, element);
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
	@SuppressWarnings("unchecked")
	public Seq<E> filter(Predicate<? super E> predicate) {
		Stack<E> list = new Stack<>(size());
		for (int i = 0; i < size(); i++) {
			E element = (E) data[i];
			if(predicate.test(element)) {
				list.push(element);
			}
		}
		return list.toSeq();
	}

	@Override
	@SuppressWarnings("unchecked")
	public <R> Seq<R> map(Function<? super E, ? extends R> mapper) {
		Object[] newData = new Object[size()];
		for (int i = 0; i < size(); i++) {
			newData[i] = mapper.apply((E) data[i]);
		}
		return new ArraySeq<>(newData);
	}

	@Override
	@SuppressWarnings("unchecked")
	public void forEach(Consumer<? super E> action) {
		for (int i = 0; i < size(); i++) {
			action.accept((E) data[i]);
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public void forEachIndexed(IndexedConsumer<? super E> action) {
		for (int i = 0; i < size(); i++) {
			action.accept(i, (E) data[i]);
		}
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
	@SuppressWarnings("unchecked")
	public Seq<E> filter(Predicate<? super E> predicate) {
		Stack<E> list = new Stack<>(size());
		for (int i = from; i < to; i++) {
			E element = (E) ref[i];
			if(predicate.test(element)) {
				list.push(element);
			}
		}
		return list.toSeq();
	}

	@Override
	@SuppressWarnings("unchecked")
	public <R> Seq<R> map(Function<? super E, ? extends R> mapper) {
		Object[] newData = new Object[size()];
		for (int ni = 0, ri = from; ri < to; ni++, ri++) {
			newData[ni] = mapper.apply((E) ref[ri]);
		}
		return new ArraySeq<>(newData);
	}

	@Override
	@SuppressWarnings("unchecked")
	public void forEach(Consumer<? super E> action) {
		for (int i = from; i < to; i++) {
			action.accept((E) ref[i]);
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public void forEachIndexed(IndexedConsumer<? super E> action) {
		for (int ni = 0, ri = from; ri < to; ni++, ri++) {
			action.accept(ni, (E) ref[ri]);
		}
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
	@SuppressWarnings("unchecked")
	public Seq<E> filter(Predicate<? super E> predicate) {
		Stack<E> list = new Stack<>(size());
		for (int i = abs(0); i > abs(size()); i--) {
			E element = (E) ref.ref[i];
			if(predicate.test(element)) {
				list.push(element);
			}
		}
		return list.toSeq();
	}

	@Override
	@SuppressWarnings("unchecked")
	public <R> Seq<R> map(Function<? super E, ? extends R> mapper) {
		Object[] newData = new Object[size()];
		for (int ni = 0, ri = abs(0); ri > abs(size()); ni++, ri--) {
			newData[ni] = mapper.apply((E) ref.ref[ri]);
		}
		return new ArraySeq<>(newData);
	}

	@Override
	@SuppressWarnings("unchecked")
	public void forEach(Consumer<? super E> action) {
		for (int i = abs(0); i > abs(size()); i--) {
			action.accept((E) ref.ref[i]);
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public void forEachIndexed(IndexedConsumer<? super E> action) {
		for (int ni = 0, ri = abs(0); ri > abs(size()); ni++, ri--) {
			action.accept(ni, (E) ref.ref[ri]);
		}
	}
}

class IllegalRangeException extends IllegalArgumentException {
	private static final long serialVersionUID = 1L;

	public IllegalRangeException(int from, int to, int size) {
		super("from: " + from + ", to: " + to + ", size: " + size);
	}
}
