package zlk.util;

@FunctionalInterface
public interface BiConsumerIndexed<T, U> {
	void accept(int idx, T t, U u);
}
