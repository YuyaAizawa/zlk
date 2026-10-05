package zlk.util;


public final class ErrorUtils {
	private ErrorUtils() {}

	public static <R> R todo(String todo) {
		throw new Todo(todo);
	}

	public static <R> R todo() {
		throw new Todo();
	}

	public static <R> R neverHappen(String reason) {
		throw new NeverHappen(reason);
	}
}

@SuppressWarnings("serial")
class Todo extends RuntimeException {
	Todo(String todo) {
		super(todo);
	}

	Todo() {
		this("to be implemented");
	}
}

@SuppressWarnings("serial")
class NeverHappen extends Error {
	NeverHappen(String reason) {
		super(reason);
	}
}