package zlk.idcalc;

import zlk.util.PrettyPrinter;

public record IcVar(
		IdInfo idInfo)
implements IcExp {

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append(idInfo.name()).append("#");
		idInfo.appendId(pp);
	}
}
