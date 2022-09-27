package zlk.idcalc;

import zlk.common.cnst.ConstValue;
import zlk.common.type.Type;
import zlk.util.Location;
import zlk.util.pp.PrettyPrinter;

public record IcCnst(
		ConstValue value,
		Location loc)
implements IcExp {

	public Type type() {
		return value().fold(
				bool -> Type.bool,
				i32  -> Type.i32);
	}

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append(value);
	}
}
