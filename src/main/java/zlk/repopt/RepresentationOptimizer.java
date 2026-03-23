package zlk.repopt;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import zlk.common.Type;
import zlk.common.id.IdMap;
import zlk.idcalc.ExpOrPattern;
import zlk.idcalc.IcExp;
import zlk.idcalc.IcExp.IcApp;
import zlk.idcalc.IcModule;

public final class RepresentationOptimizer {

	private final IcModule module;
	private final IdMap<Type> types;
	private final Map<ExpOrPattern, Type> nodeTypes;
	private final RepKeys repKeys;
	private final IdMap<SpecializationRequest> requests;

	public RepresentationOptimizer(  // public for test
			IcModule module,
			IdMap<Type> types,
			Map<ExpOrPattern, Type> nodeTypes
	) {
		this.module = module;
		this.types = types;
		this.nodeTypes = nodeTypes;
		this.repKeys = new RepKeys();
		this.requests = new IdMap<>();
	}

	public RepKeys getRepKeys() {  // for test
		return repKeys;
	}

	record Result(
		IcModule module,
		IdMap<Type> types,
		IdMap<SpecializationRequest> requests
	) {};

	public static Result optimize(
			IcModule module,
			IdMap<Type> types,
			IdentityHashMap<ExpOrPattern, Type> nodeType
	) {
		RepresentationOptimizer optimizer = new RepresentationOptimizer(module, types, nodeType);

		optimizer.collectCandidates();
		optimizer.planSpecializations();
		optimizer.applySpecializations();

		return new Result(
				optimizer.module,
				optimizer.types,
				optimizer.requests
		);
	}

	/**
	 * 可能な特殊化候補のキーを収集する
	 */
	public void collectCandidates() {  // public for test
		nodeTypes.forEach((node, type) -> {
			// UNBOXED
			if(type.in(Type.I32, Type.BOOL)) {
				repKeys.add(node, RepKey.UNBOXED);
			}

			// FIXED_TYARG
			if(node instanceof IcApp app) {
				app.fun().getId().ifPresent(id -> {
					// 型変数の具体化で特殊化できるのは直接メソッド呼び出しのみ
					List<IcExp> args = app.args();
					List<Type> declTys = types.get(id).asArrow().args();
					List<Type> actualTys = args.stream().map(arg -> nodeTypes.get(arg)).toList();
					for (int i = 0; i < args.size(); i++) {
						if(declTys.get(i) instanceof Type.Var && !(actualTys.get(i) instanceof Type.Var)) {
							repKeys.add(args.get(i), RepKey.FIXED_TYARG);
						}
					}
				});
			}
		});
	}

	/**
	 * 適用する特殊化を決定する
	 */
	private void planSpecializations() {
		// TODO Auto-generated method stub
	}

	/**
	 * 特殊化を適用する
	 *
	 * 利用する特殊化を SpecializationRequest としてまとめ
	 * 関数適用でそのIdに差し替える
	 */
	private void applySpecializations() {
		// TODO Auto-generated method stub
	}
}
