package zlk.ast;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public record App(
		List<Exp> exps)
implements Exp {

	@Override
	public <R> R map(
			Function<Const, R> forConst,
			Function<Id, R> forId,
			Function<App, R> forApp,
			Function<If, R> forIf) {
		return forApp.apply(this);
	}

	@Override
	public void match(
			Consumer<Const> forConst,
			Consumer<Id> forId,
			Consumer<App> forApp,
			Consumer<If> forIf) {
		forApp.accept(this);
	}

	@Override
	public void mkString(StringBuilder sb) {
		exps.get(0).match(
				cnst  ->  cnst.mkString(sb),
				id    ->    id.mkString(sb),
				app   ->   app.mkString(sb),
				ifExp -> ifExp.mkStringEnclosed(sb));
		for(int i = 1; i < exps.size(); i++) {
			sb.append(" ");
			exps.get(i).match(
					cnst  ->  cnst.mkString(sb),
					id    ->    id.mkString(sb),
					app   ->   app.mkStringEnclosed(sb),
					ifExp -> ifExp.mkStringEnclosed(sb));
		}
	}

}
