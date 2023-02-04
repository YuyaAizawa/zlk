package zlk.util;

import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

/**
 * 構文要素のソースコード上の範囲
 * @author YuyaAizawa
 *
 */
public record Location(
		String filename,
		Position start,
		Position end
) implements PrettyPrintable {

	private static final Location NO_LOC = new Location(null, null, null);
	public static final Location noLocation() {
		return NO_LOC;
	}

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append(filename).append(":").append(start()).append("-");
		if(start.line() == end.line()) {
			pp.append(end().column());
		} else {
			pp.append(end());
		}
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		pp(sb);
		return sb.toString();
	}
}
