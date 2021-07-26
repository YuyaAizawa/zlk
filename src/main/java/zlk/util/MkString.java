package zlk.util;

public interface MkString {

	public void mkString(StringBuilder sb);

	default String mkString() {
		StringBuilder sb = new StringBuilder();
		mkString(sb);
		return sb.toString();
	}

	default void mkStringEnclosed(StringBuilder sb) {
		sb.append("(");
		mkString(sb);
		sb.append(")");
	}
}
