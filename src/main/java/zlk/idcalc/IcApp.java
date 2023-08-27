package zlk.idcalc;

import java.util.List;

import zlk.util.Location;
import zlk.util.pp.PrettyPrinter;

public record IcApp(
		IcExp fun,
		List<IcExp> args,
		Location loc)
implements IcExp {

	@Override
	public void mkString(PrettyPrinter pp) {
		fun.match(
				cnst    -> pp.append(cnst),
				var     -> pp.append(var),
				foreign -> pp.append(foreign),
				abs     -> pp.append("(").append(abs).append(")"),
				app     -> pp.append(app),
				ifExp   -> { pp
					.append("(").endl()
					.inc().append(ifExp).endl()
					.dec().append(")");
				},
				let     -> { pp
					.append("(").endl()
					.inc().append(let)
					.dec().append(")");
				},
				letrec  -> { pp
					.append("(").endl()
					.inc().append(letrec)
					.dec().append(")");
				});
		args.forEach(arg -> {
			pp.append(" ");
			arg.match(
					cnst    -> pp.append(cnst),
					var     -> pp.append(var),
					foreign -> pp.append(foreign),
					abs     -> pp.append("(").append(abs).append(")"),
					app     -> pp.append("(").append(app).append(")"),
					ifExp   -> { pp
						.append("(").endl()
						.inc().append(ifExp).endl()
						.dec().append(")");
					},
					let     -> { pp
						.append("(").endl()
						.inc().append(let)
						.dec().append(")");
					},
					letrec  -> { pp
						.append("(").endl()
						.inc().append(letrec)
						.dec().append(")");
					});
		});
	}
}
