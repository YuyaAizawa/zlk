package zlk.idcalc;

import zlk.common.Type;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

/**
 * 識別子情報．環境で利用して重複や未定義を防ぐ．
 * @author YuyaAizawa
 *
 */
public record IdInfo(
		int id,
		String name,
		Type type)
implements PrettyPrintable {

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append(name).append("#");
		appendId(pp);
		pp.append(":").append(type);
	}

	void appendId(PrettyPrinter pp) {
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
		if(obj != null && obj instanceof IdInfo target) {
			return this.id == target.id;
		} else {
			return false;
		}
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		pp(sb);
		return sb.toString();
	}
}
