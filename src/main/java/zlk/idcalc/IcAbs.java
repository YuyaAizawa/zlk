package zlk.idcalc;

import zlk.common.Type;
import zlk.common.id.Id;
import zlk.util.Location;
import zlk.util.pp.PrettyPrinter;

public record IcAbs(
		Id id,
		Type type,
		IcExp body,
		Location loc) implements IcExp {

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append("\\").append(id).append(":").append(type).append(".").append(body);
	}
}
