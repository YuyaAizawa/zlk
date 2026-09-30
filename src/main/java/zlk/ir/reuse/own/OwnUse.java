package zlk.ir.reuse.own;

import zlk.common.Type;
import zlk.ir.reuse.LocalVar;

/**
 * Own IR上の変数出現．
 * modeはbinderの所有状態ではなく，この出現だけの利用方法を表す．
 */
public record OwnUse(LocalVar var, UseMode mode) {
	public Type type() {
		return var.type();
	}

	public boolean isTaken() {
		return mode == UseMode.TAKE;
	}
}
