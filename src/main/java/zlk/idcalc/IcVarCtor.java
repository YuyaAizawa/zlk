package zlk.idcalc;

import zlk.common.id.Id;
import zlk.common.type.Type;
import zlk.util.Location;
import zlk.util.LocationHolder;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public record IcVarCtor(
		Id id,
		Type type,
		Location loc
) implements IcExp, PrettyPrintable, LocationHolder {

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append(id);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		pp(sb);
		return sb.toString();
	}
}
