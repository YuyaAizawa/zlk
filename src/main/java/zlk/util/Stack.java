package zlk.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.function.Consumer;

public class Stack<E> implements Iterable<E> {

	private final ArrayList<E> impl;

	public Stack() {
		this(10);
	}

	public Stack(int initialCapacity) {
		impl = new ArrayList<>(initialCapacity);
	}

	public Stack(java.util.List<E> list) {
		impl = new ArrayList<>(list);
		Collections.reverse(impl);
	}

	public boolean isEmpty() {
		return impl.isEmpty();
	}

	public int size() {
		return impl.size();
	}

	public void clear() {
		impl.clear();
	}

	public void push(E e) {
		impl.add(e);
	}

	public E pop() {
		try {
			return impl.remove(impl.size()-1);
		} catch (IndexOutOfBoundsException e) {
			throw new NoSuchElementException();
		}
	}

	public E peek() {
		try {
			return impl.get(impl.size()-1);
		} catch (IndexOutOfBoundsException e) {
			throw new NoSuchElementException();
		}
	}

	public boolean remove(E e) {
		return impl.remove(e);
	}

	public java.util.List<E> getAllAsList() {
		java.util.List<E> result = new ArrayList<>();
		forEach(e -> result.add(e));
		return result;
	}

	@Override
	public Iterator<E> iterator() {
		return new Iterator<>() {

			ListIterator<E> impl = Stack.this.impl.listIterator(size());

			@Override
			public boolean hasNext() {
				return impl.hasPrevious();
			}

			@Override
			public E next() {
				return impl.previous();
			}
		};
	}

	@Override
	public void forEach(Consumer<? super E> action) {
		for(int idx = impl.size()-1; idx >= 0; idx--) {
			action.accept(impl.get(idx));
		}
	}

	@Override
	public String toString() {
		java.util.List<E> reversed = new ArrayList<>(impl);
		java.util.Collections.reverse(reversed);
		return reversed.toString();
	}

	@SuppressWarnings("rawtypes")
	private static final Stack EMPTY = new Stack(0) {
		@Override
		public void push(Object e) {
			throw new UnsupportedOperationException();
		}
	};

	/**
	 * Return an immutable empty stack for formality use.
	 * @param <E>
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public static <E> Stack<E> empty() {
		return EMPTY;
	}
}
