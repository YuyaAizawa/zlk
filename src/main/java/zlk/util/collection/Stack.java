package zlk.util.collection;

import java.util.Iterator;

public final class Stack<E> implements Iterable<E> {
	private final SeqBuffer<E> impl;

	public Stack() {
		impl = new SeqBuffer<>();
	}

	public int size() {
		return impl.size();
	}

	public boolean isEmpty() {
		return impl.isEmpty();
	}

	public void push(E element) {
		impl.add(element);
	}

	public E pop() {
		return impl.removeLast();
	}

	public E peek() {
		return impl.getLast();
	}

	public E get(int index) {
		return impl.at(impl.size() - index - 1);
	}

	public boolean contains(E element) {
		return impl.contains(element);
	}

	@Override
	public Iterator<E> iterator() {
		return impl.reverseOrder().iterator();
	}
}
