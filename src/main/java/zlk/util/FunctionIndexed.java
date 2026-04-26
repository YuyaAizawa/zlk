package zlk.util;

@FunctionalInterface
public interface FunctionIndexed<T, R> {
	R apply(int index, T value);
}
