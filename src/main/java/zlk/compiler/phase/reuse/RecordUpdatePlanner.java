package zlk.compiler.phase.reuse;

import zlk.compiler.id.IdMap;
import zlk.compiler.ir.reuse.LocalSet;
import zlk.compiler.ir.reuse.LocalVar;
import zlk.compiler.ir.reuse.own.OwnBlock;
import zlk.compiler.ir.reuse.own.OwnBranch;
import zlk.compiler.ir.reuse.own.OwnFunDecl;
import zlk.compiler.ir.reuse.own.OwnModule;
import zlk.compiler.ir.reuse.own.OwnRhs;
import zlk.compiler.ir.reuse.own.OwnStmt;
import zlk.compiler.ir.reuse.own.OwnUse;
import zlk.compiler.ir.reuse.plan.ModuleUniquenessFacts;
import zlk.compiler.ir.reuse.plan.UniquenessFacts;
import zlk.util.collection.Seq;

public final class RecordUpdatePlanner {

	/**
	 * OwnModule内のin-placeなrecord updateを行うことのできる箇所を関数ごとに返す
	 * @return 関数に対してin-placeなrecord updateが可能なsiteのlhs変数集合を返すIdMap
	 */
	public static IdMap<LocalSet> plan(OwnModule module, ModuleUniquenessFacts uniqueness) {
		IdMap<LocalSet> result = new IdMap<>();

		for(OwnFunDecl fun : module.funcs()) {
			LocalSet inplaceRecordeUpdateSites = plan(fun, uniqueness.get(fun));
			result.put(fun.id(), inplaceRecordeUpdateSites);
		}

		return result;
	}

	public static LocalSet plan(OwnFunDecl fun, UniquenessFacts uniquenessInfo) {
		RecordUpdatePlanner planner = new RecordUpdatePlanner(uniquenessInfo, fun.localIdSize());
		planner.plan(fun.body());
		return planner.inplaceRecordUpdateSites;
	}



	UniquenessFacts uniquenessInfo;
	LocalSet inplaceRecordUpdateSites;

	private RecordUpdatePlanner(UniquenessFacts uniquenessInfo, int idSize) {
		this.uniquenessInfo = uniquenessInfo;
		this.inplaceRecordUpdateSites = new LocalSet(idSize);
	}

	private void plan(OwnBlock block) {
		block.stmts().forEach(this::plan);
	}

	private void plan(OwnStmt stmt) {
		if (stmt instanceof OwnStmt.Bind(LocalVar lhs, _, OwnRhs rhs, _)) {
			switch(rhs) {
			case OwnRhs.RecordUpdate(OwnUse target, _) -> {
				if(target.isTaken() && uniquenessInfo.isUniqueAt(lhs, target.var())) {
					inplaceRecordUpdateSites.add(lhs);
				};
			}
			case OwnRhs.If(_, OwnBlock thenBlock, OwnBlock elseBlock) -> {
				plan(thenBlock);
				plan(elseBlock);
			}
			case OwnRhs.Case(_, Seq<OwnBranch> branches) -> {
				branches.forEach(branch -> plan(branch.body()));
			}
			case
			OwnRhs.Cnst _,
			OwnRhs.DirectApp _,
			OwnRhs.ClosureApp _,
			OwnRhs.MakeClosure _,
			OwnRhs.MakeRecord _,
			OwnRhs.RecordGet _ -> {}
			}
		}
	}
}
