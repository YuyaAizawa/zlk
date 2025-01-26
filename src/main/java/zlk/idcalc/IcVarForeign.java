package zlk.idcalc;

import zlk.common.Type;
import zlk.common.id.Id;
import zlk.util.Location;
import zlk.util.pp.PrettyPrinter;

public record IcVarForeign(
		Id id,
		Type type,
		Location loc)
implements IcExp {

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append(id);
	}
}
