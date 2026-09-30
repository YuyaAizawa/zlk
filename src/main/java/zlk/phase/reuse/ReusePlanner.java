package zlk.phase.reuse;

import zlk.common.id.IdMap;
import zlk.ir.reuse.LocalSet;
import zlk.ir.reuse.own.OwnModule;
import zlk.ir.reuse.plan.ModuleUniquenessFacts;
import zlk.ir.reuse.plan.ReusePlan;

public final class ReusePlanner {
	private ReusePlanner() {}

	/**
	 * 再利用計画を立てる．
	 * @param module 対象のモジュール
	 * @return 対象のモジュールに対する再利用計画
	 */
	public static ReusePlan plan(OwnModule module) {
		// 当面は関数内に留まる解析のみでの最適化
		ModuleUniquenessFacts uniquenessFacts = UniquenessAnalyzer.analyze(module);

		IdMap<LocalSet> inplaceRecordUpdateInFun = RecordUpdatePlanner.plan(module, uniquenessFacts);

		return new ReusePlan(module, inplaceRecordUpdateInFun);
	}
}
