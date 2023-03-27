package zlk.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.BiConsumer;

public class AssocList<K, V> {
	private final ArrayList<K> keys;
	private final ArrayList<V> values;

	public AssocList() {
		keys = new ArrayList<>();
		values = new ArrayList<>();
	}

	public boolean isEmpty() {
		return keys.isEmpty();
	}

	public int size() {
		return keys.size();
	}

	public boolean containsKey(K key) {
		return keys.contains(Objects.requireNonNull(key));
	}

	public List<K> keys() {
		return Collections.unmodifiableList(keys);
	}

	public List<V> values() {
		return Collections.unmodifiableList(values);
	}

	public void push(K key, V value) {
		Objects.requireNonNull(key);

		keys.add(key);
		values.add(value);
	}

	public void pop(BiConsumer<? super K, ? super V> action) {
		try {
			action.accept(keys.remove(keys.size()-1), values.remove(values.size()-1));
		} catch(IndexOutOfBoundsException e) {
			throw new NoSuchElementException();
		}
	}

	public V get(K key) {
		Objects.requireNonNull(key);

		int idx = keys.indexOf(key);
		if(idx < 0) {
			return null;
		} else {
			return values.get(idx);
		}
	}

	public V remove(K key) {
		Objects.requireNonNull(key);

		int idx = keys.indexOf(key);
		if(idx < 0) {
			return null;
		} else {
			keys.remove(idx);
			return values.remove(idx);
		}
	}

	public void forEach(BiConsumer<? super K, ? super V> action) {
		for(int idx = keys.size()-1; idx >= 0; idx--) {
			action.accept(keys.get(idx), values.get(idx));
		}
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		forEach((var, ty) -> sb.append(var).append(": ").append(ty).append(", "));
		return sb.substring(0, sb.length()-2);
	}
}
