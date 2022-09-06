package zlk.util.pp;

public interface PrettyPrintable {
	void mkString(PrettyPrinter pp);

	default void pp(Appendable out) {
		mkString(new BasicPrettyPrinter(out));
	}
}
