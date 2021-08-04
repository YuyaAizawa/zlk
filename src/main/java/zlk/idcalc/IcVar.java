package zlk.idcalc;

public record IcVar(
		String name,
		IdInfo idInfo)
implements IcExp {

	@Override
	public void mkString(StringBuilder sb) {
		sb.append(name()).append(String.format("<%04d>", idInfo.id()));
	}
}
