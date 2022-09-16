package zlk.ast;

import zlk.util.Location;
import zlk.util.pp.PrettyPrinter;

public record If(
		Exp cond,
		Exp exp1,
		Exp exp2,
		Location loc)
implements Exp {

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append("if ").append(cond).append(" then").endl();
		pp.inc().append(exp1).dec().endl();
		pp.append("else");
		if(Exp.isIf(exp2)) {
			pp.append(" ").append(exp2);
		} else {
			pp.endl();
			pp.inc().append(exp2).dec();
		}
	}
}
