package zlk.idcalc;

import zlk.common.id.Id;
import zlk.util.Location;
import zlk.util.pp.PrettyPrinter;

public record IcVar(
		Id id,
		Location loc)
implements IcExp {

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append(id);
	}
}
