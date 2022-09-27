package zlk.ast;

import zlk.common.cnst.Bool;
import zlk.common.cnst.ConstValue;
import zlk.common.cnst.I32;
import zlk.util.Location;
import zlk.util.pp.PrettyPrinter;

public record Cnst(
	ConstValue value,
	Location loc
) implements Exp {

	public Cnst(boolean value, Location loc) {
		this(value ? Bool.TRUE : Bool.FALSE, loc);
	}

	public Cnst(int value, Location loc) {
		this(new I32(value), loc);
	}

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append(value);
	}
}

