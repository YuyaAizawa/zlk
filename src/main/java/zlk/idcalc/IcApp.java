package zlk.idcalc;

import java.util.List;

import zlk.util.PrettyPrinter;

public record IcApp(
		IcExp fun,
		List<IcExp> args)
implements IcExp {

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append(fun);
		args.forEach(arg -> {
			pp.append(" ");
			arg.match(
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
						.inc().append(let)
						.dec().append(")");
					});
		});
	}
}
