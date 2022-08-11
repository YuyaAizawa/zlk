package zlk.idcalc;

public record IcVar(
		IdInfo idInfo
) implements IcExp {

	@Override
	public void mkString(StringBuilder sb) {
		idInfo.mkString(sb);
	}
}
