package zlk.ast;

import java.util.function.Consumer;
import java.util.function.Function;

public record Bool(
		boolean value)
implements Const {

	static final Bool TRUE = new Bool(true);
	static final Bool FALSE = new Bool(false);

	@Override
	public <R> R map(
			Function<Bool, R> forBool,
			Function<I32, R> forI32) {
		return forBool.apply(this);
	}

	@Override
	public void match(
			Consumer<Bool> forBool,
			Consumer<I32> forI32) {
		forBool.accept(this);
	}
}