package zlk.common.cnst;

import zlk.util.pp.PrettyPrinter;

public record Bool(
		boolean value
) implements ConstValue {
	public static final Bool TRUE = new Bool(true);
	public static final Bool FALSE = new Bool(false);

	@Override
	public void mkString(PrettyPrinter pp) {
		if(value) {
			pp.append("true");
		} else {
			pp.append("false");
		}
	}
}