package zlk.util;

@FunctionalInterface
public interface ConsumerIndexed<T> {
	void accept(int idx, T value);
}
