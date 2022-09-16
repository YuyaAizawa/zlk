package zlk.common.cnst;

import zlk.util.pp.PrettyPrinter;

public record I32(
		int value)
implements ConstValue {

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append(value);
	}
}
