package zlk.util;

public record Location(int line, int column) implements MkString {
	@Override
	public void mkString(StringBuilder sb) {
		sb.append(line);
		sb.append(":");
		sb.append(column);
	}
}
