package zlk.clcalc;

import zlk.common.id.Id;
import zlk.common.type.Type;
import zlk.util.Location;
import zlk.util.pp.PrettyPrinter;

public record CcVar(
		Id id,
		Location loc)
implements CcExp {

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.field("var", id);
	}

	public Type type() {
		return id.type();
	}
}
