package zlk.ast;

import java.util.function.Consumer;
import java.util.function.Function;

public record I32(
		int value)
implements Const {
	@Override
	public <R> R map(
			Function<Bool, R> forBool,
			Function<I32, R> forI32) {
		return forI32.apply(this);
	}

	@Override
	public void match(
			Consumer<Bool> forBool,
			Consumer<I32> forI32) {
		forI32.accept(this);
	}
}
