package zlk.util;

@FunctionalInterface
public interface BiFunctionIndexed<T, U, R> {
	R apply(int index, T t, U u);
}
