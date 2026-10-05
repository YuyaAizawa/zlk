package zlk.compiler.ir.reuse.plan;

import zlk.compiler.ir.reuse.LocalMap;
import zlk.compiler.ir.reuse.LocalVar;
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
