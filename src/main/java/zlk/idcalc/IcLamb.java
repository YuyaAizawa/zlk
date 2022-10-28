package zlk.idcalc;

import zlk.common.id.Id;
import zlk.common.type.Type;
import zlk.util.Location;
import zlk.util.pp.PrettyPrinter;

public record IcLamb(
		Id lambId,
		Id varId,
		Type varType,
		IcExp body,
		Location loc
) implements IcExp {

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append("\\").append(varId).append(" : ").append(varType).append(".").endl();
		pp.inc().append(body).dec();
	}
}
