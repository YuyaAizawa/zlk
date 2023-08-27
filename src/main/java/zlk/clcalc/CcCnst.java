package zlk.clcalc;

import zlk.common.cnst.ConstValue;
import zlk.common.type.TyAtom;
import zlk.common.type.Type;
import zlk.util.Location;
import zlk.util.pp.PrettyPrinter;

public record CcCnst(
		ConstValue value,
		Location loc
) implements CcExp {

	public Type type() {
		return value().fold(
				bool -> TyAtom.BOOL,
				i32  -> TyAtom.I32);
	}

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.field("const", value);
	}
}
