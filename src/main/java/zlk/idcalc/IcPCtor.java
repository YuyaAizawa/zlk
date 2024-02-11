package zlk.idcalc;

import java.util.List;

import zlk.util.Location;
import zlk.util.pp.PrettyPrinter;

public record IcPCtor(
		IcVarCtor ctor,
		List<IcPCtorArg> args,
		Location loc) implements IcPattern {

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append(ctor);
		for(IcPCtorArg arg: args) {
			pp.append(" ");
			arg.pattern().match(
					var   -> pp.append(var),
					pctor -> pp.append("(").append(pctor).append(")"));
		}
	}
}


