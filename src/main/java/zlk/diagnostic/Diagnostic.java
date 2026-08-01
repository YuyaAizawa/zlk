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

	/**
	 * 名前解決診断における名前空間
	 */
	public enum NameNamespace {
		/** 値（関数・局所束縛を含む）． */
		VALUE,
		/** nominal typeおよびtype alias． */
		TYPE,
		/** ADTのデータコンストラクタ． */
		CONSTRUCTOR
	}

	/**
	 * 型引数のkind
	 *
	 * resolver内部のkind表現は公開しない．
	 */
	public enum TypeKind {
		/** 通常の値型． */
		TYPE,
		/** recordの拡張部分を表すrow． */
		ROW
	}

	/**
	 * 型不一致の理由
	 *
	 * 単一化器内部の理由表現とは独立に保つ．
	 */
	public enum TypeMismatchReason {
		/** 互換でない二つの型を単一化しようとした． */
		INCOMPATIBLE,
		/** 異なるkindの型を単一化しようとした． */
		KIND,
		/** rowに存在してはならないfieldが現れた． */
		ROW_LACKS,
		/** rowの展開が循環した． */
		RECURSIVE_ROW
	}

	/**
	 * 型診断の発生した構文
	 *
	 * constraintや単一化器の内部表現は公開しない．
	 */
	public sealed interface TypingContext
	permits TypingContext.Annotation, TypingContext.CallArgument, TypingContext.CallArity,
			TypingContext.FieldAccess, TypingContext.IfCondition, TypingContext.None {
		/** 型注釈の検査中． */
		record Annotation(String declaration) implements TypingContext {}
		/** 関数呼び出しの引数を検査中． */
		record CallArgument(String function, int index) implements TypingContext {}
		/** 関数呼び出しの引数個数を検査中． */
		record CallArity(String function, int actual) implements TypingContext {}
		/** record fieldのアクセスまたは更新を検査中． */
		record FieldAccess(String field) implements TypingContext {}
		/** if式の条件を検査中． */
		record IfCondition() implements TypingContext {}
		/** より具体的な構文contextを特定できない． */
		record None() implements TypingContext {}
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

	// ================== typing ==================

	record TypeMismatch(
			Location location,
			TypingContext context,
			TypeMismatchReason reason
	) implements Diagnostic {
		@Override
		public Severity severity() {
			return Severity.ERROR;
		}
	}

	record InfiniteType(Location location, String declaration) implements Diagnostic {
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
