package zlk.common;

import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

/**
 * 識別子情報．環境で利用して重複や未定義を防ぐ．
 * @author YuyaAizawa
 *
 */
public class Id implements PrettyPrintable {

	private final int id;
	private final String name;
	private final Type type;

	Id(int id, String name, Type type) {
		this.id = id;
		this.name = name;
		this.type = type;
	}

	public Type type() {
		return type;
	}

	public String name() {
		return name;
	}

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append(name).append("#");
		appendId(pp);
	}

	public PrettyPrintable ppWithType() {
		return pp -> {
			pp.append(this).append(":").append(type);
		};
	}

	private void appendId(PrettyPrinter pp) {
		if(id < 10) {
			pp.append("000");
		} else if(id < 100) {
			pp.append("00");
		} else if(id < 1000) {
			pp.append("0");
		} else if(id < 10000){

		} else {
			throw new RuntimeException("too big id: "+id);
		}
		pp.append(id);
	}

	@Override
	public int hashCode() {
		return Integer.hashCode(id);
	}

	@Override
	public boolean equals(Object obj) {
		if(obj != null && obj instanceof Id target) {
			return this.id == target.id;
		} else {
			return false;
		}
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		ppWithType().pp(sb);
		return sb.toString();
	}
}
