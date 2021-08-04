package zlk.ast;

public record Id(
		String name)
implements Exp {

	@Override
	public void mkString(StringBuilder sb) {
		sb.append(name);
	}
}
