package zlk.util;

@FunctionalInterface
public interface IntFunctionIndexed<R> {
	R apply(int index, int value);
}
