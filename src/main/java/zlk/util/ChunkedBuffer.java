package zlk.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.IntFunction;

public class ChunkedBuffer<E> {
	private static final int DEFAULT_HEAD_CHUNK_SIZE = 10;

	private final IntFunction<E[]> arrayBuilder;
	private final List<E[]> chunks = new ArrayList<>();
	private E[] tailChunk;  // length == chunkSize, null if not initialized
	private int tailSize;   // 0..chunkSize
	private int size;

	public ChunkedBuffer(IntFunction<E[]> arrayBuilder) {
		this(arrayBuilder, DEFAULT_HEAD_CHUNK_SIZE);
	}

	public ChunkedBuffer(IntFunction<E[]> arrayBuilder, int initialCapacity) {
		this.arrayBuilder = arrayBuilder;
		int cap = Math.max(initialCapacity, DEFAULT_HEAD_CHUNK_SIZE);
		tailChunk = arrayBuilder.apply(cap);
		chunks.add(tailChunk);
		tailSize = 0;
		size = 0;
	}

	public void add(E e) {
		if (tailSize == tailChunk.length) { // tail is full
			int newCap = tailChunk.length + (tailChunk.length >> 1);
			tailChunk = arrayBuilder.apply(newCap);
			chunks.add(tailChunk);
			tailSize = 0;
		}
		tailChunk[tailSize++] = e;
		size++;
	}

	public int size() {
		return size;
	}

	public void forEach(Consumer<E> action) {
		Objects.requireNonNull(action);

		int last = chunks.size() - 1;
		for (int i = 0; i < last; i++) {
			E[] chunk = chunks.get(i);
			for (int j = 0; j < chunk.length; j++) {
				action.accept(chunk[j]);
			}
		}
		for (int i = 0; i < tailSize; i++) {
			action.accept(tailChunk[i]);
		}
	}

	public E[] toArray() {
		E[] result = arrayBuilder.apply(size);
		int copied = 0;

		int last = chunks.size() - 1;
		for (int i = 0; i < last; i++) {
			E[] chunk = chunks.get(i);
			System.arraycopy(chunk, 0, result, copied, chunk.length);
			copied += chunk.length;
		}
		System.arraycopy(tailChunk, 0, result, copied, tailSize);
		assert copied + tailSize == size;
		return result;
	}

	public List<E> toList() {
		List<E> result = new ArrayList<>(size);
		int copied = 0;

		int last = chunks.size() - 1;
		for (int i = 0; i < last; i++) {
			E[] chunk = chunks.get(i);
			result.addAll(List.of(chunk));
			copied += chunk.length;
		}
		result.addAll(List.of(tailChunk));
		assert copied + tailSize == size;
		return result;
	}

	public Iterator<E> iterator() {
		return new Iterator<>() {
			private int consumed = 0;
			private int chunkIndex = 0;
			private int indexInChunk = 0;

			@Override
			public boolean hasNext() {
				return consumed < size;
			}

			@Override
			public E next() {
				if (!hasNext()) {
					throw new NoSuchElementException();
				}

				E[] chunk = chunks.get(chunkIndex);
				int limit = (chunkIndex == chunks.size() - 1) ? tailSize : chunk.length;

				if (indexInChunk >= limit) {
					chunkIndex++;
					indexInChunk = 0;
					chunk = chunks.get(chunkIndex);
				}

				consumed++;
				return chunk[indexInChunk++];
			}
		};
	}
}