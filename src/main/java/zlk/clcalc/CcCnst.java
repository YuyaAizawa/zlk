package zlk.clcalc;

import zlk.common.cnst.ConstValue;
import zlk.common.type.TyBase;
import zlk.common.type.Type;
import zlk.util.Location;
import zlk.util.pp.PrettyPrinter;

public record CcCnst(
		ConstValue value,
		Location loc
) implements CcExp {

	public Type type() {
		return value().fold(
				bool -> TyBase.BOOL,
				i32  -> TyBase.I32);
	}

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.field("const", value);
	}
}
