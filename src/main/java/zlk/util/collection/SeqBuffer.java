package zlk.util.collection;

import java.util.ConcurrentModificationException;
import java.util.EmptyStackException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import zlk.util.ConsumerIndexed;


/// 可変リストデータ構造
///
/// Stackという名前だがFILOになるのはpopするときだけで，forEachやtoSeqはFIFO
///
/// 用途
/// - 集約操作
/// - スタック
///
/// 実装メモ
/// 配列を連結リストで繋いだもの
///
/// ```
/// headChunk         tailChunk
///     |                 |
///     v                 v
///  |-----|-----|-----|-----| <- add/removeLast箇所
///    <-- prev     next -->
///   ==forEach/toSeq方向==>
/// ```
///
/// ArrayListのreallocが少しもったいないと思ったのとSeqを使うために作った
///
/// @param <E>

public final class SeqBuffer<E> implements Iterable<E> {

	static final int DEFAULT_CHUNK_SIZE = 10;
	static final int MAX_CHUNK_SIZE = 4000;

	private Chunk tailChunk;
	private Chunk headChunk;
	private Chunk holdChunk;  // removeLastしてchunkが空になったとき，再利用のためにしばらく取っておく
	private int tailSize;  // tailChunkの有効な要素の数
	private int totalSize;

	private int modCount = 0;

	public SeqBuffer(int capacityHint) {
		int chunkSize = Math.clamp(capacityHint, DEFAULT_CHUNK_SIZE, MAX_CHUNK_SIZE);
		Chunk newChunk = new Chunk(new Object[chunkSize]);
		tailChunk = newChunk;
		headChunk = newChunk;
		holdChunk = newChunk;
		tailSize = 0;
		totalSize = 0;
	}

	public SeqBuffer() {
		holdChunk = null;
		totalSize = 0;
		grow();
		headChunk = tailChunk;
	}

	public SeqBuffer(SeqBuffer<E> original) {
		int rest = original.size();
		int chunkSize = Math.clamp(rest, DEFAULT_CHUNK_SIZE, MAX_CHUNK_SIZE);

		Chunk newChunk = new Chunk(new Object[chunkSize]);
		headChunk = newChunk;
		tailChunk = newChunk;
		holdChunk = null;
		tailSize = 0;
		totalSize = 0;

		addAll(original);
	}

	private void grow() {
		Chunk newChunk = holdChunk;
		holdChunk = null;
		if(newChunk == null) {
			int chunkSize = Math.clamp(totalSize, DEFAULT_CHUNK_SIZE, MAX_CHUNK_SIZE);
			newChunk = new Chunk(new Object[chunkSize]);
		}
		if(tailChunk != null) {
			tailChunk.next = newChunk;
		}
		newChunk.prev = tailChunk;
		tailChunk = newChunk;
		tailSize = 0;
	}

	private void shrink() {
		holdChunk = tailChunk;
		tailChunk = tailChunk.prev;
		tailChunk.next = null;
		tailSize = tailChunk.data.length;
	}

	public int size() {
		return totalSize;
	}

	public boolean isEmpty() {
		return totalSize == 0;
	}

	public void add(E element) {
		if(tailSize == tailChunk.data.length) {
			grow();
		}
		tailChunk.data[tailSize++] = element;
		totalSize++;
		modCount++;
	}

	@SuppressWarnings("unchecked")
	public E removeLast() {
		if(isEmpty()) {
			throw new EmptyStackException();
		}
		if(tailSize == 0) {
			shrink();
		}

		E element = (E) tailChunk.data[--tailSize];
		tailChunk.data[tailSize] = null;
		totalSize--;
		modCount++;

		if(tailSize < (tailChunk.data.length >> 1)) {
			holdChunk = null;
		}

		return element;
	}

	@SuppressWarnings("unchecked")
	public E getLast() {
		if(isEmpty()) {
			throw new EmptyStackException();
		}
		if(tailSize == 0) {
			return (E) tailChunk.prev.data[tailChunk.prev.data.length - 1];
		}
		return (E) tailChunk.data[tailSize - 1];
	}

	@SuppressWarnings("unchecked")
	public E at(int index) {
		if(index < 0 || totalSize <= index) {
			throw new ArrayIndexOutOfBoundsException(index);
		}

		int count = 0;
		Chunk cursor = headChunk;
		while(true) {
			if(index - count < cursor.data.length) {
				return (E) cursor.data[index - count];
			}
			count += cursor.data.length;
			cursor = cursor.next;
		}
	}

	public void addAll(SeqBuffer<E> elements) {
		if(elements.isEmpty()) {
			return;
		}
		if(elements == this) {
			elements = new SeqBuffer<>(elements);
		}

		for(Chunk cursor = elements.headChunk; cursor != null; cursor = cursor.next) {
			int srcLength = cursor == elements.tailChunk ? elements.tailSize : cursor.data.length;
			int srcIndex = 0;

			while(srcIndex < srcLength) {
				if(tailSize == tailChunk.data.length) {
					grow();
				}

				int copyLength = Math.min(srcLength - srcIndex, tailChunk.data.length - tailSize);
				System.arraycopy(cursor.data, srcIndex, tailChunk.data, tailSize, copyLength);
				srcIndex += copyLength;
				tailSize += copyLength;
				totalSize += copyLength;
			}
		}
		modCount++;
	}

	public void addAll(Seq<E> elements) {
		if(elements.isEmpty()) {
			return;
		}

		Object[] data;
		int srcIndex;
		if(elements instanceof ArraySeq<E> arraySeq) {
			data = arraySeq.data;
			srcIndex = 0;
		} else if(elements instanceof SliceSeq<E> sliceSeq) {
			data = sliceSeq.ref;
			srcIndex = sliceSeq.from;
		} else {
			elements.forEach(this::add);
			return;
		}
		int srcLength = elements.size();

		while(srcIndex < srcLength) {
			if(tailSize == tailChunk.data.length) {
				grow();
			}

			int copyLength = Math.min(srcLength - srcIndex, tailChunk.data.length - tailSize);
			System.arraycopy(data, srcIndex, tailChunk.data, tailSize, copyLength);
			srcIndex += copyLength;
			tailSize += copyLength;
			totalSize += copyLength;
		}
		modCount++;
	}

	/// 指定した要素が最初に出現するインデックスを返す．含まれていなければ-1．
	/// @param element
	public int indexOf(E element) {
		if(isEmpty()) {
			return -1;
		}
		int count = 0;
		Chunk cursor = headChunk;
		if(element == null) {
			while(true) {
				int length = cursor == tailChunk ? tailSize : cursor.data.length;
				for(int i = 0; i < length; i++) {
					if(cursor.data[i] == null) {
						return count + i;
					}
				}
				count += cursor.data.length;
				cursor = cursor.next;
				if(cursor == null) {
					break;
				}
			}
		} else {
			while(true) {
				int length = cursor == tailChunk ? tailSize : cursor.data.length;
				for(int i = 0; i < length; i++) {
					if(element.equals(cursor.data[i])) {
						return count + i;
					}
				}
				count += cursor.data.length;
				cursor = cursor.next;
				if(cursor == null) {
					break;
				}
			}
		}
		return -1;
	}

	/// 指定した要素が含まれているか返す
	/// @param element
	public boolean contains(E element) {
		return indexOf(element) >= 0;
	}

	/// 指定した要素がこのバッファに含まれなければ追加する
	///
	/// @param element
	public boolean addIfNotContains(E element) {
		if(!contains(element)) {
			add(element);
			return true;
		}
		return false;
	}

	@SuppressWarnings("unchecked")
	public void forEachIndexed(ConsumerIndexed<? super E> action) {
		if(isEmpty()) {
			return;
		}
		int count = 0;
		Chunk cursor = headChunk;

		while(true) {
			int length = cursor == tailChunk ? tailSize : cursor.data.length;
			for(int i = 0; i < length; i++) {
				action.accept(count++, (E) cursor.data[i]);
			}
			cursor = cursor.next;
			if(cursor == null) {
				break;
			}
			length = cursor == tailChunk ? tailSize : cursor.data.length;
		}
	}

	@Override
	public void forEach(Consumer<? super E> action) {
		ConsumerIndexed<? super E> ignoreIndex = (_, e) -> action.accept(e);
		forEachIndexed(ignoreIndex);
	}

	public SeqBuffer<E> filter(Predicate<? super E> predicate) {
		SeqBuffer<E> result = new SeqBuffer<>();
		forEach(e -> {
			if(predicate.test(e)) {
				result.add(e);
			}
		});
		return result;
	}

	public E[] toArray(IntFunction<E[]> builder) {
		if(isEmpty()) {
			return builder.apply(0);
		}

		E[] result = builder.apply(size());
		int count = 0;
		for(Chunk c = headChunk; c != tailChunk; c = c.next) {
			System.arraycopy(c.data, 0, result, count, c.data.length);
			count += c.data.length;
		}
		System.arraycopy(tailChunk.data, 0, result, count, tailSize);

		return result;
	}

	public Seq<E> toSeq() {
		if(isEmpty()) {
			return Seq.of();
		}

		Object[] array = new Object[totalSize];
		int count = 0;

		for(Chunk cursor = headChunk; cursor != null; cursor = cursor.next) {
			int length = cursor == tailChunk ? tailSize : cursor.data.length;
			for(int i = 0; i < length; i++) {
				array[count++] = cursor.data[i];
			}
		}

		return new ArraySeq<>(array);
	}

	@Override
	public Iterator<E> iterator() {
		return new Iterator<>() {

			int index = 0;
			Chunk cursor = headChunk;
			int length = cursor == tailChunk ? tailSize : cursor.data.length;
			{ if(length == 0) { cursor = null;} }

			int expectedModCount = modCount;

			@Override
			public boolean hasNext() {
				return cursor != null;
			}

			@Override
			@SuppressWarnings("unchecked")
			public E next() {
				if(!hasNext()) {
					throw new NoSuchElementException();
				}
				if(expectedModCount != modCount) {
					throw new ConcurrentModificationException();
				}

				E result = (E) cursor.data[index++];

				if(index == length) {
					index = 0;
					cursor = cursor.next;
					if(cursor != null) {
						length = cursor == tailChunk ? tailSize : cursor.data.length;
					}
				}

				return result;
			}

			@Override
			@SuppressWarnings("unchecked")
			public void forEachRemaining(Consumer<? super E> action) {
				if(expectedModCount != modCount) {
					throw new ConcurrentModificationException();
				}
				while(true) {
					for(;index < length; index++) {
						action.accept((E) cursor.data[index]);
					}
					index = 0;
					cursor = cursor.next;
					if(cursor == null) {
						break;
					}
					length = cursor == tailChunk ? tailSize : cursor.data.length;
				}
			}
		};
	}

	Stream<E> stream() {
		return StreamSupport.stream(spliterator(), false);
	}

	static private class Chunk {
		final Object[] data;
		Chunk prev;
		Chunk next;

		Chunk(Object[] data) {
			this.data = data;
		}
	}

	/**
	 * 逆順のイテレータ処理のための{@link Iterable}を返す．
	 * @return
	 */
	public Iterable<E> reverseOrder() {
		return () -> new Iterator<>() {

			Chunk cursor = tailChunk;
			int index = cursor == tailChunk ? tailSize - 1 : cursor.data.length - 1;
			{ if(index < 0) { cursor = null;} }

			int expectedModCount = modCount;

			@Override
			public boolean hasNext() {
				return cursor != null;
			}

			@Override
			@SuppressWarnings("unchecked")
			public E next() {
				if(!hasNext()) {
					throw new NoSuchElementException();
				}
				if(expectedModCount != modCount) {
					throw new ConcurrentModificationException();
				}

				E result = (E) cursor.data[index--];
				if(index < 0) {
					cursor = cursor.prev;
					index = cursor == null ? -1 : cursor.data.length - 1;
				}
				return result;
			}

			@Override
			@SuppressWarnings("unchecked")
			public void forEachRemaining(Consumer<? super E> action) {
				if(expectedModCount != modCount) {
					throw new ConcurrentModificationException();
				}
				while(cursor != null) {
					for(;index >= 0; index--) {
						action.accept((E) cursor.data[index]);
					}
					cursor = cursor.prev;
					index = cursor == null ? -1 : cursor.data.length - 1;
				}
			};
		};
	}
}
