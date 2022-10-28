package zlk.common.id;

import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

/**
 * 識別子情報．環境で利用して重複や未定義を防ぐ．名前空間にもなる．
 * @author YuyaAizawa
 *
 */
public final class Id implements PrettyPrintable {

	public static final String SEPARATOR = ".";

	private final String parent;
	private final String simpleName;

	public static Id fromCanonicalName(String canonical) {
		int lastSeparatorIndex = canonical.lastIndexOf(SEPARATOR);
		return new Id(
				canonical.substring(0, Math.max(lastSeparatorIndex, 0)),
				canonical.substring(lastSeparatorIndex + 1));
	}

	public static Id fromPathAndSimpleName(String path, String simple) {
		return new Id(path, simple);
	}

	private Id(String parent, String simpleName) {
		this.parent = parent.intern();
		this.simpleName = simpleName.intern();
	}

	public String simpleName() {
		return simpleName;
	}

	public String canonicalName() {
		return parent.isEmpty()
				? simpleName
				: parent + SEPARATOR + simpleName;
	}

	public Id child(String simple) {
		return new Id(canonicalName(), simple);
	}

	@Override
	public void mkString(PrettyPrinter pp) {
		if(parent.isEmpty()) {
			pp.append(simpleName);
		} else {
			pp.append(parent).append(SEPARATOR).append(simpleName);
		}

	}

	@Override
	public int hashCode() {
		return parent.hashCode() * 255 + simpleName.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if(obj == null || obj.getClass() != Id.class) {
			return false;
		}
		Id other = (Id) obj;

		return other.simpleName == this.simpleName && other.parent == this.parent;
	}

	@Override
	public String toString() {
		return canonicalName();
	}
}
