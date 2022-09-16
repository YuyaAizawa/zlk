package zlk.ast;

import java.util.List;

import zlk.util.Location;
import zlk.util.pp.PrettyPrinter;

public record App(
		List<Exp> exps,
		Location loc)
implements Exp {

	@Override
	public void mkString(PrettyPrinter pp) {
		exps.get(0).match(
				cnst  -> pp.append(cnst),
				id    -> pp.append(id),
				app   -> pp.append(app),
				ifExp -> { pp
					.append("(").endl()
					.inc().append(ifExp).endl()
					.dec().append(")");
				},
				let   ->  {
					pp.append("(").endl()
					.inc().append(let).endl()
					.dec().append(")");
				});
		for(int i = 1; i < exps.size(); i++) {
			pp.append(" ");
			exps.get(i).match(
					cnst  -> pp.append(cnst),
					id    -> pp.append(id),
					app   -> pp.append("(").append(app).append(")"),
					ifExp -> { pp
						.append("(").endl()
						.inc().append(ifExp).endl()
						.dec().append(")");
					},
					let   -> { pp
						.append("(").endl()
						.inc().append(let).endl()
						.dec().append(")");
					});
		}
	}
}
