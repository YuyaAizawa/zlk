package zlk.util.collection;

import java.util.ConcurrentModificationException;
import java.util.EmptyStackException;
import java.util.NoSuchElementException;
import java.util.PrimitiveIterator;
import java.util.Spliterators;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

import zlk.util.ConsumerIndexed;

/// {@link SeqBuffer} のint版
/// ちょっとメモリ節約
public final class IntSeqBuffer implements Iterable<Integer>{

	private static final int DEFAULT_CHUNK_SIZE = 10;
	private static final int MAX_CHUNK_SIZE = 4000;

	private Chunk tailChunk;
	private Chunk headChunk;
	private Chunk holdChunk;  // popしてchunkが空になったとき，再利用のためにしばらく取っておく
	private int tailSize;  // tailChunkの有効な要素の数
	private int totalSize;

	private int modCount = 0;

	public IntSeqBuffer(int capacityHint) {
		int chunkSize = Math.clamp(capacityHint, DEFAULT_CHUNK_SIZE, MAX_CHUNK_SIZE);
		Chunk newChunk = new Chunk(new int[chunkSize]);
		tailChunk = newChunk;
		tailSize = 0;
		headChunk = tailChunk;
	}

	public IntSeqBuffer() {
		this(DEFAULT_CHUNK_SIZE);
	}

	private void grow() {
		Chunk newChunk = holdChunk;
		holdChunk = null;
		if(newChunk == null) {
			int chunkSize = Math.clamp(totalSize, DEFAULT_CHUNK_SIZE, MAX_CHUNK_SIZE);
			newChunk = new Chunk(new int[chunkSize]);
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

	public void add(int element) {
		if(tailSize == tailChunk.data.length) {
			grow();
		}
		tailChunk.data[tailSize++] = element;
		totalSize++;
	}

	public int removeLast() {
		if(isEmpty()) {
			throw new EmptyStackException();
		}
		if(tailSize == 0) {
			shrink();
		}

		int element = tailChunk.data[--tailSize];
		totalSize--;
		modCount++;

		if(tailSize < (tailChunk.data.length >> 1)) {
			holdChunk = null;
		}

		return element;
	}

	public int getLast() {
		if(isEmpty()) {
			throw new EmptyStackException();
		}
		if(tailSize == 0) {
			return tailChunk.prev.data[tailChunk.prev.data.length - 1];
		}
		return tailChunk.data[tailSize - 1];
	}

	public int get(int index) {
		if(index < 0 || totalSize <= index) {
			throw new ArrayIndexOutOfBoundsException(index);
		}

		int count = 0;
		Chunk cursor = headChunk;
		while(true) {
			if(index - count < cursor.data.length) {
				return cursor.data[index - count];
			}
			count += cursor.data.length;
			cursor = cursor.next;
		}
	}

	public boolean contains(int element) {
		if(isEmpty()) {
			return false;
		}
		Chunk cursor = headChunk;
		while(cursor != null) {
			int length = cursor == tailChunk ? tailSize : cursor.data.length;
			for(int i = 0; i < length; i++) {
				if(element == cursor.data[i]) {
					return true;
				}
			}
			cursor = cursor.next;
			if(cursor == null) {
				break;
			}
		}
		return false;
	}

	@SuppressWarnings("unchecked")
	public void forEachIndexed(ConsumerIndexed<Integer> action) {
		if(isEmpty()) {
			return;
		}
		int count = 0;
		Chunk cursor = headChunk;

		while(true) {
			int length = cursor == tailChunk ? tailSize : cursor.data.length;
			for(int i = 0; i < length; i++) {
				action.accept(count++, cursor.data[i]);
			}
			cursor = cursor.next;
			if(cursor == null) {
				break;
			}
			length = cursor == tailChunk ? tailSize : cursor.data.length;
		}
	}

	public int[] toArray() {
		if(isEmpty()) {
			return new int[0];
		}

		int[] result = new int[size()];
		int count = 0;
		for(Chunk c = headChunk; c != tailChunk; c = c.next) {
			System.arraycopy(c.data, 0, result, count, c.data.length);
			count += c.data.length;
		}
		System.arraycopy(tailChunk.data, 0, result, count, tailSize);

		return result;
	}

	public IntSeq toSeq() {
		if(isEmpty()) {
			return IntSeq.of();
		}

		int[] array = new int[totalSize];
		int count = 0;

		for(Chunk cursor = headChunk; cursor != null; cursor = cursor.next) {
			int length = cursor == tailChunk ? tailSize : cursor.data.length;
			for(int i = 0; i < length; i++) {
				array[count++] = cursor.data[i];
			}
		}

		return new IntArraySeq(array);
	}

	@Override
	public PrimitiveIterator.OfInt iterator() {
		return new PrimitiveIterator.OfInt() {

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
			public int nextInt() {
				if(!hasNext()) {
					throw new NoSuchElementException();
				}
				if(expectedModCount != modCount) {
					throw new ConcurrentModificationException();
				}

				int result = cursor.data[index++];

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
			public void forEachRemaining(IntConsumer action) {
				if(expectedModCount != modCount) {
					throw new ConcurrentModificationException();
				}
				while(true) {
					for(;index < length; index++) {
						action.accept(cursor.data[index]);
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

	IntStream stream() {
		return StreamSupport.intStream(Spliterators.spliteratorUnknownSize(iterator(), 0), false);
	}

	static private class Chunk {
		final int[] data;
		Chunk prev;
		Chunk next;

		Chunk(int[] data) {
			this.data = data;
		}
	}

	/**
	 * 逆順のイテレータ処理のための{@link Iterable}を返す．
	 * @return
	 */
	public Iterable<Integer> reverseOrder() {
		return () -> new PrimitiveIterator.OfInt() {

			Chunk cursor = tailChunk;
			int index = cursor == tailChunk ? tailSize - 1 : cursor.data.length - 1;
			{ if(index < 0) { cursor = null;} }

			int expectedModCount = modCount;

			@Override
			public boolean hasNext() {
				return cursor != null;
			}

			@Override
			public int nextInt() {
				if(!hasNext()) {
					throw new NoSuchElementException();
				}
				if(expectedModCount != modCount) {
					throw new ConcurrentModificationException();
				}

				int result = cursor.data[index--];
				if(index < 0) {
					cursor = cursor.prev;
					index = cursor == null ? -1 : cursor.data.length - 1;
				}
				return result;
			}

			@Override
			public void forEachRemaining(IntConsumer action) {
				if(expectedModCount != modCount) {
					throw new ConcurrentModificationException();
				}
				while(cursor != null) {
					for(;index >= 0; index--) {
						action.accept(cursor.data[index]);
					}
					cursor = cursor.prev;
					index = cursor == null ? -1 : cursor.data.length - 1;
				}
			};
		};
	}
}
