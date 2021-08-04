package zlk.idcalc;

import zlk.common.Type;

/**
 * 識別子情報．環境で利用して重複や未定義を防ぐ．
 * @author YuyaAizawa
 *
 */
public final class IdInfo {
	private final int id;
	private final Info info;

	public IdInfo(int id, Info info) {
		this.id = id;
		this.info = info;
	}

	public int id() {
		return id;
	}

	public Info info() {
		return info;
	}

	public Type type() {
		return info.type();
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
