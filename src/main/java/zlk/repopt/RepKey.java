package zlk.repopt;

public enum RepKey {
	/**
	 * primitiveまたはspecialized layoutで保持または受け渡ししてよい．
	 */
	UNBOXED,

	/**
	 * calleeまたはパターン出現であるとき，その型引数がこの出現位置では具体化してよい．
	 */
	FIXED_TYARG,

//	/**
//	 * 値は一度だけ消費される前提を置ける
//	 * また，callee出現であるとき，所有権をcalleeに渡せる
//	 */
//	LINEAR,
	;
}
