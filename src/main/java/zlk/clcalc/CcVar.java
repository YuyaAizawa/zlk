package zlk.clcalc;

import zlk.common.Id;
import zlk.common.Type;
import zlk.util.pp.PrettyPrinter;

public record CcVar(
		Id id)
implements CcExp {

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.field("var", id);
	}

	public Type type() {
		return id.type();
	}
}
