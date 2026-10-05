package zlk.compiler.ir.reuse.own;

/**
 * 変数の各use siteが所有参照をどのように利用するか．
 */
public enum UseMode {
	/** 所有参照を消費せず，一時的に値を参照する． */
	BORROW,
	/** 所有参照1つを呼出し先，戻り値，格納先などへ移す．uniqueであることは意味しない． */
	TAKE;
}
