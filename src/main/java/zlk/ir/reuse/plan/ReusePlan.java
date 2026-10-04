package zlk.ir.reuse.plan;

import zlk.common.TypeDecl;
import zlk.common.id.Id;
import zlk.common.id.IdMap;
import zlk.ir.reuse.LocalSet;
import zlk.ir.reuse.LocalVar;
import zlk.ir.reuse.own.OwnFunDecl;
import zlk.ir.reuse.own.OwnModule;
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
