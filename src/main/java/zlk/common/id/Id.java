package zlk.common.id;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

/**
 * 識別子を表す．名前空間にもなり，最終的なバイトコード上の衝突を防ぐ．
 * 内部で利用する文法上妥当でない文字も含む．
 * `==`で同値比較可能．
 * @author YuyaAizawa
 *
 */
public final class Id implements PrettyPrintable, Comparable<Id> {

	public static final String SEPARATOR = ".";
	private static final Pattern SEPARATOR_REGEX = Pattern.compile("\\.");

	// ==で比較するための機構
	private static final Map<Key, Id> pool = new HashMap<>();
	private record Key(Id parent, String simple) {};  // 構造は今はIdと一緒だがキー用を分ける

	private final Id parent; // トップレベルはnull
	private final String simple;

	private Id(Id parent, String simple) {
		this.parent = parent;
		this.simple = simple;
	}

	// TODO: Idもinternできるように

	/**
	 * 親と名前がSEPARATORで区切られた識別子を用意する
	 * @param parent 親
	 * @param simple 名前
	 * @return 識別子
	 */
	public static Id intern(Id parent, String simple) {
		simple = simple.intern();
		Key key = new Key(parent, simple);
		Id existing = pool.get(key);
		if(existing != null) {
			return existing;
		}
		Id created = new Id(parent, simple);
		pool.put(key, created);
		return created;
	}

	/**
	 * 文字列から識別子を用意する
	 * @param canonical 文字列
	 * @return 識別子
	 */
	public static Id intern(String canonical) {
		if(canonical.isBlank()) {
			throw new IllegalArgumentException(canonical);
		}
		String[] elements = SEPARATOR_REGEX.split(canonical);
		Id result = null;
		for(int i = 0; i < elements.length; i++) {
			result = intern(result, elements[i].intern());
		}
		return result;
	}

	public Id parent() {
		return parent;
	}

	public String simpleName() {
		return simple;
	}

	public String canonicalName() {
		StringBuilder sb = new StringBuilder();

		List<String> parts = list();
		sb.append(parts.getFirst());
		for(String part : parts.subList(1, parts.size())) {
			sb.append(SEPARATOR);
			sb.append(part);
		}
		return sb.toString();
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
		return obj == this;
	}

	@Override
	public String toString() {
		return buildString();
	}

	@Override
	public int compareTo(Id o) {
		List<String> ts = this.list();
		List<String> os = o.list();

		for(int i = 0; ts.size() > i && os.size() > i; i++) {
			int cmp = ts.get(i).compareTo(os.get(i));
			if(cmp != 0) {
				return cmp;
			}
		}
		return ts.size() - os.size();
	}

	private List<String> list() {
		List<String> acc = new ArrayList<>();
		for(Id cursor = this; cursor != null; cursor = cursor.parent) {
			acc.add(cursor.simple);
		}
		return acc.reversed();
	}
}
