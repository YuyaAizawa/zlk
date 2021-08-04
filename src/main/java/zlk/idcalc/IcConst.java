package zlk.idcalc;

import zlk.ast.Const;
import zlk.common.Type;

public record IcConst(
		Const cnst)
implements IcExp {

	public Type type() {
		return cnst().fold(
				bool -> Type.bool,
				i32  -> Type.i32);
	}

	@Override
	public void mkString(StringBuilder sb) {
		cnst().mkString(sb);
	}
}
