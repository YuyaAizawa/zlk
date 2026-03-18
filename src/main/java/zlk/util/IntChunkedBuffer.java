package zlk.util;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.PrimitiveIterator;
import java.util.function.IntConsumer;

/**
 * 要素数の不明なintを一時的に集約するための構造．
 * 集約フェーズのあとの読取りフェーズが完全に分離されていることを想定している．
 */
public final class IntChunkedBuffer {
	private static final int DEFAULT_HEAD_CHUNK_SIZE = 10;

	private final List<int[]> chunks = new ArrayList<>();
	private int[] tailChunk; // length == chunkSize, null if not initialized
	private int tailSize; // 0..chunkSize
	private int size;

	public IntChunkedBuffer() {
		this(DEFAULT_HEAD_CHUNK_SIZE);
	}

	public IntChunkedBuffer(int initialCapacity) {
		int cap = Math.max(initialCapacity, DEFAULT_HEAD_CHUNK_SIZE);
		tailChunk = new int[cap];
		chunks.add(tailChunk);
		tailSize = 0;
		size = 0;
	}

	public void add(int e) {
		if (tailSize == tailChunk.length) { // tail is full
			int newCap = tailChunk.length + (tailChunk.length >> 1);
			tailChunk = new int[newCap];
			chunks.add(tailChunk);
			tailSize = 0;
		}
		tailChunk[tailSize++] = e;
		size++;
	}

	public int size() {
		return size;
	}

	public void forEach(IntConsumer action) {
		Objects.requireNonNull(action);

		int last = chunks.size() - 1;
		for (int i = 0; i < last; i++) {
			int[] chunk = chunks.get(i);
			for (int j = 0; j < chunk.length; j++) {
				action.accept(chunk[j]);
			}
		}
		for (int i = 0; i < tailSize; i++) {
			action.accept(tailChunk[i]);
		}
	}

	public int[] toArray() {
		int[] result = new int[size];
		int copied = 0;

		int last = chunks.size() - 1;
		for (int i = 0; i < last; i++) {
			int[] chunk = chunks.get(i);
			System.arraycopy(chunk, 0, result, copied, chunk.length);
			copied += chunk.length;
		}
		System.arraycopy(tailChunk, 0, result, copied, tailSize);
		assert copied + tailSize == size;
		return result;
	}

	public PrimitiveIterator.OfInt iterator() {
		return new PrimitiveIterator.OfInt() {
			private int consumed = 0;
			private int chunkIndex = 0;
			private int indexInChunk = 0;

			@Override
			public boolean hasNext() {
				return consumed < size;
			}

			@Override
			public int nextInt() {
				if (!hasNext()) {
					throw new NoSuchElementException();
				}

				int[] chunk = chunks.get(chunkIndex);
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
