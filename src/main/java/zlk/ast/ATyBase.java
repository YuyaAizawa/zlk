package zlk.ast;

import zlk.util.Location;
import zlk.util.pp.PrettyPrinter;

public record ATyBase(
		String name,
		Location loc
) implements AType {

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append(toString());
	}

	@Override
	public String toString() {
		// TODO Auto-generated method stub
		return name;
	}
}
