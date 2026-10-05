package zlk.compiler.ir.reuse.plan;

import zlk.compiler.id.IdMap;
import zlk.compiler.ir.reuse.own.OwnFunDecl;

public final class ModuleUniquenessFacts {
	private final IdMap<UniquenessFacts> impl;

	public ModuleUniquenessFacts(IdMap<UniquenessFacts> impl) {
		this.impl = impl;
	}

	public UniquenessFacts get(OwnFunDecl fun) {
		return impl.get(fun.id());
	}
}
