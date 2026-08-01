package zlk.diagnostic;

import zlk.common.Location;
import zlk.util.collection.Seq;

/**
 * コンパイル中に検出した診断
 *
 * エラーメッセージの生成やログに利用する予定
 */
public sealed interface Diagnostic {

	/**
	 * 重要度
	 *
	 * <ul>
	 * <li> {@link #ERROR}: コンパイルを失敗させる問題
	 * <li> {@link #WARN}: コンパイルを失敗させない潜在的な問題
	 * <li> {@link #INFO}: 問題なく実行された処理の情報
	 * </ul>
	 */
	public enum Severity {
		ERROR,
		WARN,
		INFO
	}

	/** Source namespace used by a name-resolution diagnostic. */
	public enum NameNamespace {
		VALUE,
		TYPE,
		CONSTRUCTOR
	}

	/** Public kind vocabulary; it intentionally does not expose resolver internals. */
	public enum TypeKind {
		TYPE,
		ROW
	}

	Severity severity();

	default boolean isError() {
		return severity() == Severity.ERROR;
	}

	// ================== parse ==================

	record SyntaxError(Location location) implements Diagnostic {
		@Override
		public Severity severity() {
			return Severity.ERROR;
		}
	}

	// ================== nameeval ==================

	record UnknownName(Location location, String name) implements Diagnostic {
		@Override
		public Severity severity() {
			return Severity.ERROR;
		}
	}

	record DuplicateName(
			Location location,
			NameNamespace namespace,
			String name,
			Location previousLocation
	) implements Diagnostic {
		@Override
		public Severity severity() {
			return Severity.ERROR;
		}
	}

	record TypeArityMismatch(
			Location location,
			String name,
			int expected,
			int actual
	) implements Diagnostic {
		@Override
		public Severity severity() {
			return Severity.ERROR;
		}
	}

	record RecursiveTypeAlias(
			Location location,
			String alias,
			Location cycleLocation
	) implements Diagnostic {
		@Override
		public Severity severity() {
			return Severity.ERROR;
		}
	}

	record DuplicateTypeParameter(
			Location location,
			String name,
			Location previousLocation
	) implements Diagnostic {
		@Override
		public Severity severity() {
			return Severity.ERROR;
		}
	}

	record UndeclaredTypeVariable(Location location, String name) implements Diagnostic {
		@Override
		public Severity severity() {
			return Severity.ERROR;
		}
	}

	record TypeKindMismatch(
			Location location,
			String subject,
			TypeKind expectedKind,
			TypeKind actualKind,
			Location previousLocation
	) implements Diagnostic {
		@Override
		public Severity severity() {
			return Severity.ERROR;
		}
	}

	record DuplicateRecordField(
			Location location,
			String fieldName,
			Location previousLocation
	) implements Diagnostic {
		@Override
		public Severity severity() {
			return Severity.ERROR;
		}
	}

	record ConstructorArityMismatch(
			Location location,
			String constructor,
			int expected,
			int actual
	) implements Diagnostic {
		@Override
		public Severity severity() {
			return Severity.ERROR;
		}
	}

	// ================== patterncheck ==================

	record IncompletePattern(
			Location location,
			Seq<PatternWitness> examples
	) implements Diagnostic {
		@Override
		public Severity severity() {
			return Severity.ERROR;
		}
	}

	record RedundantPattern(
			Location overallLocation,
			Location patternLocation,
			int caseIndex
	) implements Diagnostic {
		@Override
		public Severity severity() {
			return Severity.ERROR;
		}
	}
}
