package zlk.idcalc;

import zlk.common.ConstValue;
import zlk.common.Type;
import zlk.util.Location;
import zlk.util.pp.PrettyPrinter;

public record IcCnst(
		ConstValue value,
		Location loc)
implements IcExp {

	public Type.Atom type() {
		return switch(value) {
		case ConstValue.Bool _ -> Type.BOOL;
		case ConstValue.I32 _ -> Type.I32;
		};
	}

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append(value);
	}
}
