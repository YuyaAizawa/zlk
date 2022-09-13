package zlk.clcalc;

import zlk.ast.Const;
import zlk.common.Type;
import zlk.util.pp.PrettyPrinter;

public record CcConst(
		Const cnst
) implements CcExp {

	public Type type() {
		return cnst().fold(
				bool -> Type.bool,
				i32  -> Type.i32);
	}

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.field("const", cnst);
	}
}
