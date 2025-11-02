package zlk.common.id;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

/**
 * 識別子情報．環境で利用して重複や未定義を防ぐ．名前空間にもなる．
 *
 * @author YuyaAizawa
 *
 */
public final class Id implements PrettyPrintable, Comparable<Id> {

	public static final String SEPARATOR = ".";
	private static final Pattern SEPARATOR_REGEX = Pattern.compile("\\.");

	private final Id parent; // nullable
	private final String simple;

	/**
	 * 識別子を新しく生成する
	 * @param canonical
	 * @return
	 */
	public static Id fromCanonicalName(String canonical) {
		if(canonical.isBlank()) {
			throw new IllegalArgumentException(canonical);
		}
		String[] elements = SEPARATOR_REGEX.split(canonical);
		Id result = null;
		for(int i = 0; i < elements.length; i++) {
			result = new Id(result, elements[i].intern());
		}
		return result;
	}

	public static Id fromParentAndSimpleName(Id parent, String simple) {
		return new Id(parent, simple.intern());
	}

	private Id(Id parent, String simple) {
		this.parent = parent;
		this.simple = simple;
	}

	public Id parent() {
		return parent;
	}

	public String simpleName() {
		return simple;
	}

	public String canonicalName() {
		StringBuilder sb = new StringBuilder();
		canonicalNameHelp(sb);
		return sb.toString();
	}
	private void canonicalNameHelp(StringBuilder sb) {
		if(parent != null) {
			parent.canonicalNameHelp(sb);
			sb.append(SEPARATOR);
		}
		sb.append(simple);
	}

	@Override
	public void mkString(PrettyPrinter pp) {
		if(parent != null) {
			pp.append(parent);
			pp.append(SEPARATOR);
		}
		pp.append(simple);
	}

	@Override
	public int hashCode() {
		if(parent != null) {
			return parent.hashCode() * 31 + simple.hashCode();
		} else {
			return simple.hashCode();
		}
	}

	@Override
	public boolean equals(Object obj) {
		if(obj == this) {
			return true;
		}
		if(obj == null || obj.getClass() != Id.class) {
			return false;
		}
		return equals((Id) obj);
	}

	private boolean equals(Id other) {
		if(other == null) {
			return false;
		}
		if(this.simple != other.simple) {
			return false;
		}
		if(this.parent == null) {
			return other.parent == null;
		}
		return this.parent.equals(other.parent);
	}

	@Override
	public String toString() {
		return canonicalName();
	}

	@Override
	public int compareTo(Id o) {
		List<String> ts = this.list();
		List<String> os = o.list();

		int i = 0;
		while(ts.size() > i && os.size() > i) {
			int cmp = ts.get(i).compareTo(os.get(i));
			if(cmp != 0) {
				return cmp;
			}
			i++;
		}
		return ts.size() - os.size();
	}

	private List<String> list() {
		List<String> result = new ArrayList<>();
		listHelp(result);
		return result;
	}
	private void listHelp(List<String> acc) {
		if(parent != null) {
			parent.listHelp(acc);
		}
		acc.add(simple);
	}
}
