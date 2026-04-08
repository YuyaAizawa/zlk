package zlk.util;

@FunctionalInterface
public interface IndexedConsumer<T> {
	void accept(int idx, T value);
}
