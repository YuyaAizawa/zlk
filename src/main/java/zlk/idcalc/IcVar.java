package zlk.idcalc;

import zlk.common.Id;
import zlk.util.pp.PrettyPrinter;

public record IcVar(
		Id idInfo)
implements IcExp {

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append(idInfo);
	}
}
