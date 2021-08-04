package zlk.ast;

import java.util.List;

public record App(
		List<Exp> exps)
implements Exp {

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
