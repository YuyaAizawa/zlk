package zlk.clcalc;

import zlk.common.ConstValue;
import zlk.common.Type;
import zlk.util.Location;
import zlk.util.pp.PrettyPrinter;

public record CcCnst(
		ConstValue value,
		Location loc
) implements CcExp {

	public Type type() {
		return switch(value) {
		case ConstValue.Bool _ -> Type.BOOL;
		case ConstValue.I32 _ -> Type.I32;
		};
	}

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.field("const", value);
	}
}
