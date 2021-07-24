package zlk.ast;

import java.util.function.Consumer;
import java.util.function.Function;

public record Id(
		String name)
implements Exp {

	@Override
	public <R> R map(
			Function<Const, R> forConst,
			Function<Id, R> forId,
			Function<App, R> forApp) {
		return forId.apply(this);
	}

	@Override
	public void match(
			Consumer<Const> forConst,
			Consumer<Id> forId,
			Consumer<App> forApp) {
		forId.accept(this);
	}

	@Override
	public void mkString(StringBuilder sb) {
		sb.append(name);
	}
}
