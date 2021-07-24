package zlk.ast;

import java.util.function.Consumer;
import java.util.function.Function;

public record App(
		Exp fun,
		Exp arg)
implements Exp {

	@Override
	public <R> R map(
			Function<Const, R> forConst,
			Function<Id, R> forId,
			Function<App, R> forApp) {
		return forApp.apply(this);
	}

	@Override
	public void match(
			Consumer<Const> forConst,
			Consumer<Id> forId,
			Consumer<App> forApp) {
		forApp.accept(this);
	}

	@Override
	public void mkString(StringBuilder sb) {
		fun.mkString(sb);
		sb.append(" ");
		arg.match(
				cnst -> cnst.mkString(sb),
				id -> id.mkString(sb),
				app -> app.mkStringEnclosed(sb));
	}

}
