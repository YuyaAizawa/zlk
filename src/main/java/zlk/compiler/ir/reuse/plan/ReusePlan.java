package zlk.compiler.ir.reuse.plan;

import zlk.compiler.id.Id;
import zlk.compiler.id.IdMap;
import zlk.compiler.ir.reuse.LocalSet;
import zlk.compiler.ir.reuse.LocalVar;
import zlk.compiler.ir.reuse.own.OwnFunDecl;
import zlk.compiler.ir.reuse.own.OwnModule;
import zlk.compiler.ir.typing.TypeDecl;
import zlk.util.collection.Seq;

public final class ReusePlan {
	private final OwnModule module;
	private final IdMap<LocalSet> inplaceRecordUpdateInFun;  // 最適化しない関数は含めなくても良い

	public ReusePlan(OwnModule module, IdMap<LocalSet> inplaceRecordUpdateInFun) {
		this.module = module;
		this.inplaceRecordUpdateInFun = inplaceRecordUpdateInFun;
	}

	public String name() {
		return module.name();
	}

	public Seq<TypeDecl> types() {
		return module.types();
	}

	public Seq<OwnFunDecl> funcs() {
		return module.funcs();
	}

	public boolean isInplaceRecordUpdate(Id fun, LocalVar site) {
		return inplaceRecordUpdateInFun.getOptional(fun)
				.map(sites -> sites.contains(site))
				.orElse(false);
	}

}
