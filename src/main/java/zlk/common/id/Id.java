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
		return new Id(canonical);
	}

	public static Id fromPathAndSimpleName(String path, String simple) {
		return new Id(path, simple);
	}

	public static Id fromParentAndSimpleName(Id parent, String simple) {
		return new Id(parent.canonicalName(), simple);
	}

	private Id(String parent, String simpleName) {
		this.parent = parent.intern();
		this.simpleName = simpleName.intern();
	}
	private Id(String canonicalName) {
		this(canonicalName, canonicalName.lastIndexOf(SEPARATOR));
	}
	private Id(String canonicalName, int lastDotIndex) {
		this(
				canonicalName.substring(0, Math.max(lastDotIndex, 0)),
				canonicalName.substring(lastDotIndex+1));
	}

	public String simpleName() {
		return simpleName;
	}

	public String canonicalName() {
		return parent.isEmpty()
				? simpleName
				: parent + SEPARATOR + simpleName;
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
