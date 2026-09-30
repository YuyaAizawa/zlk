package zlk.ir.reuse.plan;

import zlk.ir.reuse.LocalMap;
import zlk.ir.reuse.LocalVar;
import zlk.util.collection.Seq;

public final class UniquenessFacts {
	private final LocalMap<Seq<LocalVar>> impl;  // 各siteに関連するuniqueな変数（site自身を除く）

	public UniquenessFacts(LocalMap<Seq<LocalVar>> impl) {
		this.impl = impl;
	}

	public Uniqueness getUniquenessAt(LocalVar site, LocalVar var) {
		return impl.getOrElse(site, Seq.of()).contains(var)
				? Uniqueness.UNIQUE
				: Uniqueness.UNKNOWN;
	}

	public boolean isUniqueAt(LocalVar site, LocalVar var) {
		return getUniquenessAt(site, var) == Uniqueness.UNIQUE;
	}
}
