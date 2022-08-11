package zlk.idcalc;

import zlk.common.Type;
import zlk.util.MkString;

/**
 * 識別子情報．環境で利用して重複や未定義を防ぐ．
 * @author YuyaAizawa
 *
 */
public record IdInfo(
		int id,
		String name,
		Type type
) implements MkString {

	public String name() {
		return name == "" ? String.format("$%04d", id) : name;
	}

	@Override
	public void mkString(StringBuilder sb) {
		sb.append(name());
	}

	@Override
	public int hashCode() {
		return id;
	}

	@Override
	public boolean equals(Object obj) {
		if(obj != null && obj instanceof IdInfo target) {
			return this.id == target.id;
		} else {
			return false;
		}
	}
}
