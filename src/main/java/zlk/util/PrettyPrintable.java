package zlk.util;

public interface PrettyPrintable {
	void mkString(PrettyPrinter pp);

	default void pp(Appendable out) {
		mkString(new PrettyPrinter(out));
	}
}
