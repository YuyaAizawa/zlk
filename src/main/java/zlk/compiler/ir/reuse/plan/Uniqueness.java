package zlk.compiler.ir.reuse.plan;

public enum Uniqueness {
	UNIQUE,  // 他の変数から参照されていないことが確定

	UNKNOWN;
}
