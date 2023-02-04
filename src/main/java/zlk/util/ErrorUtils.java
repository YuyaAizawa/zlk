package zlk.util;

public final class ErrorUtils {
	private ErrorUtils() {}

	public static <R> R todo() {
		throw new RuntimeException("to be implemented");
	}

	public static <R> R neverHappen(String reason, Location loc) {
		return neverHappen(reason+"@"+loc);
	}

	public static <R> R neverHappen(String reason) {
		throw new Error(reason);
	}
}