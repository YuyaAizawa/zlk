package zlk.util.collection;

import java.util.ConcurrentModificationException;
import java.util.EmptyStackException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Consumer;
import java.util.function.IntFunction;
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
///  |-----|-----|-----|-----| <- push/pop箇所
///    <-- prev     next -->
///   ==forEach/toSeq方向==>
/// ```
///
/// ArrayListのreallocが少しもったいないと思ったのとSeqを使うために作った
///
/// @param <E>

public class Stack<E> implements Iterable<E> {

	static final int DEFAULT_CHUNK_SIZE = 10;
	static final int MAX_CHUNK_SIZE = 4000;

	private Chunk tailChunk;
	private Chunk headChunk;
	private Chunk holdChunk;  // popしてchunkが空になったとき，再利用のためにしばらく取っておく
	private int tailSize;  // tailChunkの有効な要素の数
	private int totalSize;

	private int modCount = 0;

	public Stack(int capacityHint) {
		int chunkSize = Math.clamp(capacityHint, DEFAULT_CHUNK_SIZE, MAX_CHUNK_SIZE);
		Chunk newChunk = new Chunk(new Object[chunkSize]);
		tailChunk = newChunk;
		tailSize = 0;
		headChunk = tailChunk;
	}

	public Stack() {
		grow();
		headChunk = tailChunk;
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

	public void push(E element) {
		if(tailSize == tailChunk.data.length) {
			grow();
		}
		tailChunk.data[tailSize++] = element;
		totalSize++;
	}

	@SuppressWarnings("unchecked")
	public E pop() {
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
	public E peek() {
		if(isEmpty()) {
			throw new EmptyStackException();
		}
		if(tailSize == 0) {
			return (E) tailChunk.prev.data[tailChunk.prev.data.length - 1];
		}
		return (E) tailChunk.data[tailSize - 1];
	}

	@SuppressWarnings("unchecked")
	public E get(int index) {
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

	public boolean contains(E element) {
		if(isEmpty()) {
			return false;
		}
		Chunk cursor = headChunk;
		if(element == null) {
			while(true) {
				int length = cursor == tailChunk ? tailSize : cursor.data.length;
				for(int i = 0; i < length; i++) {
					if(cursor.data[i] == null) {
						return true;
					}
				}
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
						return true;
					}
				}
				cursor = cursor.next;
				if(cursor == null) {
					break;
				}
			}
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
