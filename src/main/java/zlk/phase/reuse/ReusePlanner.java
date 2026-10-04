package zlk.phase.reuse;

import zlk.common.id.IdMap;
import zlk.ir.reuse.LocalSet;
import zlk.ir.reuse.own.OwnModule;
import zlk.ir.reuse.plan.ModuleUniquenessFacts;
import zlk.ir.reuse.plan.ReusePlan;
import zlk.phase.CompilationOptions;
import zlk.phase.CompilationOptions.Key;

public final class ReusePlanner {
	private ReusePlanner() {}

	/**
	 * 再利用計画を立てる．
	 * @param module 対象のモジュール
	 * @return 対象のモジュールに対する再利用計画
	 */
	public static ReusePlan plan(OwnModule module, CompilationOptions options) {
		// 当面は関数内に留まる解析のみでの最適化
		ModuleUniquenessFacts uniquenessFacts = UniquenessAnalyzer.analyze(module);

		IdMap<LocalSet> inplaceRecordUpdateInFun = options.isEnabled(Key.OPT_REUSE_RECORD)
				? RecordUpdatePlanner.plan(module, uniquenessFacts)
				: IdMap.of();

		return new ReusePlan(module, inplaceRecordUpdateInFun);
	}
	public static ReusePlan plan(OwnModule module) {
		return plan(module, CompilationOptions.DEFAULT);
	}
}
