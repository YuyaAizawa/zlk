package zlk.compiler.diagnostic;

import java.util.Optional;

import zlk.compiler.id.Id;
import zlk.compiler.ir.typing.Type;
import zlk.compiler.source.Location;
import zlk.util.collection.Seq;

/**
 * コンパイル中に検出した診断
 *
 * 各variantは，rendererやIDEがphase内部のIRを参照せずに表示できる，
 * source-facingなpayloadを持つ．{@link zlk.compiler.driver.Driver}は発生順に診断を収集し，
 * {@link Severity#ERROR}を一件以上含む場合は後続phaseを実行せず
 * {@code CompilationResult.Failed}を返す．{@link Severity#WARN}と
 * {@link Severity#INFO}だけの場合は，診断列を保持した
 * {@code CompilationResult.Succeeded}を返す．
 */
public sealed interface Diagnostic {

	/**
	 * 重要度
	 *
	 * <ul>
	 * <li> {@link #ERROR}: 後続phaseをblockしコンパイルを失敗させる問題
	 * <li> {@link #WARN}: コンパイルを継続する潜在的な問題
	 * <li> {@link #INFO}: コンパイルを継続する情報
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
		TypingContext NONE = new None();
		TypingContext IF_CONDITION = new IfCondition();

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

	/**
	 * 値宣言またはADT constructorに対して解決された型．
	 *
	 * @param location 宣言の位置
	 * @param declaration 宣言の識別子
	 * @param type 再構築後の安定した型
	 */
	record InferredType(Location location, Id declaration, Type type) implements Diagnostic {
		@Override
		public Severity severity() {
			return Severity.INFO;
		}
	}

	/**
	 * Bytecode生成において一つのRHSの生成が完了したことを表すtrace．
	 *
	 * source診断ではなく，明示的に有効化した場合だけ報告するbackend情報である．
	 * {@code localId}は{@code function}内でのみLocalVarを識別する．phase内部の
	 * LocalVar自体は公開しない．
	 *
	 * @param location RHSに対応するsource位置
	 * @param function 生成中の関数
	 * @param localId 関数内のLocalVar識別子
	 * @param variable source上の名前を持つ場合の識別子
	 */
	record BytecodeStmt(
			Location location,
			Id function,
			int localId,
			Optional<Id> variable
	) implements Diagnostic {
		@Override
		public Severity severity() {
			return Severity.INFO;
		}
	}

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

	/**
	 * Constructor patternのfamilyが期待されたADTと異なる．
	 *
	 * 型再構築が検出する診断だが，利用者がpatternに関する問題として
	 * 他の型不一致と区別できるように独立したvariantとする．
	 *
	 * @param location 不一致を起こしたconstructor patternの位置
	 * @param constructor 不一致を起こしたconstructor
	 * @param actualFamily constructorが属するADT
	 * @param expectedFamily pattern位置で期待されたADT
	 */
	record ConstructorFamilyMismatch(
			Location location,
			Id constructor,
			Id actualFamily,
			Id expectedFamily
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
