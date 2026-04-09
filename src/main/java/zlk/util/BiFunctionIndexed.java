package zlk.util;

@FunctionalInterface
public interface BiFunctionIndexed<T, U, R> {
	R accumulate(int index, T t, U u);
}
