package zlk.util;

import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public record Position(int line, int column) implements PrettyPrintable {
	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append(line).append(":").append(column);
	}
}
