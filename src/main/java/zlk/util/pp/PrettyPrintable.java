package zlk.util.pp;

public interface PrettyPrintable {
	void mkString(PrettyPrinter pp);

	default void pp(Appendable out) {
		mkString(new BasicPrettyPrinter(out));
	}

	default String buildString() {
		StringBuilder sb = new StringBuilder();
		mkString(new BasicPrettyPrinter(sb));
		return sb.toString();
	}
}
