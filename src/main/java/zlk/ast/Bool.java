package zlk.ast;

public record Bool(
		boolean value
) implements Const {
	static final Bool TRUE = new Bool(true);
	static final Bool FALSE = new Bool(false);
}