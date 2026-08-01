package zlk.phase.recon.constraint;

import java.util.Optional;

import zlk.common.id.Id;
import zlk.phase.recon.constraint.Context.Annotation;
import zlk.phase.recon.constraint.Context.CallArg;
import zlk.phase.recon.constraint.Context.CallArity;
import zlk.phase.recon.constraint.Context.FieldAccess;
import zlk.phase.recon.constraint.Context.IfCondition;
import zlk.phase.recon.constraint.Context.None;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

/**
 * 型が期待される理由のうち文脈が関係するもの
 */
public sealed interface Context extends PrettyPrintable
permits Annotation, CallArg, CallArity, FieldAccess, IfCondition, None {

	public static final Context NONE = new None();
	public static final Context IF_CONDITION = new IfCondition();

	/** 型注釈 */
	record Annotation(Id id) implements Context {}

	/** フィールドアクセス */
	record FieldAccess(String field) implements Context {}

	/** 特になし */
	record None() implements Context {}

	/**
	 * 関数呼び出しの引数
	 *
	 * @param maybeName 呼び出された関数（直接の呼び出しでなければempty）
	 * @param index 引数のインデックス
	 */
	record CallArg(Optional<Id> maybeName, int index) implements Context {}

	/**
	 * 関数呼び出しの引数の数
	 *
	 * @param maybeName 呼び出された関数（直接の呼び出しでなければempty）
	 * @param length 引数の数
	 */
	record CallArity(Optional<Id> maybeName, int length) implements Context {}

	/**
	 * ifの条件部分
	 */
	record IfCondition() implements Context {}

	@Override
	default void mkString(PrettyPrinter pp) {
		switch(this) {
		case Annotation(Id id) -> pp.append("annotation: ").append(id);
		case CallArg(Optional<Id> maybeName, int index) -> {
			PrettyPrintable name = maybeName
					.map(id -> (PrettyPrintable)(p -> p.append(id)))
					.orElse(p -> p.append("(no id)"));
			pp.append("callee: ").append(name).append(", index: ").append(index);
		}
		case CallArity(Optional<Id> maybeName, int length) -> {
			PrettyPrintable name = maybeName
					.map(id -> (PrettyPrintable)(p -> p.append(id)))
					.orElse(p -> p.append("(no id)"));
			pp.append("callee: ").append(name).append(", arity: ").append(length);
		}
		case IfCondition() -> {
			pp.append("if condition");
		}
		case FieldAccess(String field) -> pp.append("field access: ").append(field);
		case None() -> pp.append("no context");
		}
	}
}
