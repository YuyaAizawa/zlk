package zlk.idcalc;

import zlk.common.id.Id;
import zlk.util.Location;
import zlk.util.pp.PrettyPrinter;

public record IcVarLocal(
		Id id,
		Location loc)
implements IcExp {

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append(id);
	}
}
