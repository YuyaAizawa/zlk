package zlk.idcalc;

import zlk.common.ConstValue;
import zlk.common.type.TyAtom;
import zlk.util.Location;
import zlk.util.pp.PrettyPrinter;

public record IcCnst(
		ConstValue value,
		Location loc)
implements IcExp {

	public TyAtom type() {
		return switch(value) {
		case ConstValue.Bool _ -> TyAtom.BOOL;
		case ConstValue.I32 _ -> TyAtom.I32;
		};
	}

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append(value);
	}
}
