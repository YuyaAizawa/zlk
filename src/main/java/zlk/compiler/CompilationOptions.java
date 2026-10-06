package zlk.compiler;

import java.util.EnumSet;

/**
 * コンパイルオプションの組合せ
 */
public final class CompilationOptions {

	public enum Key {
		// 診断出力系
		REPORT_INFERRED_TYPES,
		REPORT_BYTECODE_STMT_ORDER,

		// 最適化系
		OPT_REUSE_RECORD,
		OPT_USE_OPERAND_STACK,
	}

	/**
	 * 既定のコンパイルオプション
	 */
	public static final CompilationOptions DEFAULT =
			new CompilationOptions(EnumSet.of(
					Key.OPT_REUSE_RECORD,
					Key.OPT_USE_OPERAND_STACK));

	private final EnumSet<Key> keys;
	private CompilationOptions(EnumSet<Key> keys) {
		this.keys = keys;
	}

	public boolean isEnabled(Key key) {
		return keys.contains(key);
	}

	public CompilationOptions set(Key key, boolean value) {
		if(isEnabled(key) == value) {
			return this;
		}

		EnumSet<Key> nextKeys = EnumSet.copyOf(keys);
		if(value) {
			nextKeys.add(key);
		} else {
			nextKeys.remove(key);
		}
		return new CompilationOptions(nextKeys);
	}

	public CompilationOptions enable(Key key) {
		return set(key, true);
	}

	public CompilationOptions disable(Key key) {
		return set(key, false);
	}
}
