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

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append(filename).append(":").append(start()).append("-");
		if(start.line() == end.line()) {
			pp.append(end().column());
		} else {
			pp.append(end());
		}
	}
}
