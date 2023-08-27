package zlk.idcalc;

import zlk.common.cnst.ConstValue;
import zlk.common.type.TyAtom;
import zlk.util.Location;
import zlk.util.pp.PrettyPrinter;

public record IcCnst(
		ConstValue value,
		Location loc)
implements IcExp {

	public TyAtom type() {
		return value().fold(
				bool -> TyAtom.BOOL,
				i32  -> TyAtom.I32);
	}

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append(value);
	}
}
