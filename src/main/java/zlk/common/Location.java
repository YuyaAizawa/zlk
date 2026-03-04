package zlk.common;

import zlk.parser.Source;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

/**
 * 構文要素のソースコード上の範囲
 * 表示はエディタ用で内部表現はchar index
 * @author YuyaAizawa
 *
 */
public final class Location implements PrettyPrintable {

	private final Source src;
	private final int start;  // inclusive
	private final int end;    // exclusive

	public Location(Source src, int start, int end) {
		this.src = src;
		this.start = start;
		this.end = end;
	}

	private static final Location NO_LOC = new Location(null, 0, 0);
	public static final Location noLocation() {
		return NO_LOC;
	}

	public static Location range(Location from, Location to) {
		if(from == NO_LOC) { return to; }
		if(to == NO_LOC) { return from; }

		if(from.src != to.src) {
			throw new IllegalArgumentException(
					"src not match: "+from.src.fileName+" /= "+to.src.fileName);
		}
		if(from.start > to.end) {
			throw new IllegalArgumentException(
					"invalid char index: "+from.start+" > "+to.end);
		}
		return new Location(from.src, from.start, to.end);
	}

	@Override
	public void mkString(PrettyPrinter pp) {
		Position start = src.getPosition(this.start);
		Position end = src.getPosition(this.end);
		pp.append(start).append("-").append(end).append("@").append(src.fileName);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		pp(sb);
		return sb.toString();
	}
}
