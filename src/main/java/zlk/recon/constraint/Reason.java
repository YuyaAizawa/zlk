package zlk.recon.constraint;

import zlk.common.Type;
import zlk.common.id.Id;
import zlk.recon.constraint.Reason.FromAnnotation;
import zlk.recon.constraint.Reason.FromContext;
import zlk.recon.constraint.Reason.NoExpectation;
import zlk.util.Location;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

/**
 * 期待される型の理由
 */
public sealed interface Reason extends PrettyPrintable
permits NoExpectation, FromContext, FromAnnotation {

	// TODO エラーメッセージを生成するSupplierにできないか検討（テスタビリティ微妙だが）

	public static final Reason NO_EXPECTATION = new NoExpectation();

	/**
	 * 期待される型はない
	 */
	record NoExpectation() implements Reason {}

	/**
	 * 文脈による理由
	 *
	 * @param ctx 理由の文脈
	 * @param 期待される型の位置
	 */
	record FromContext(Context ctx, Location loc) implements Reason {}

	/**
	 * 型注釈による理由
	 *
	 * @param name 型注釈された変数の名前
	 * @param type 型注釈
	 */
	record FromAnnotation(Id name, Type type) implements Reason {}

	@Override
	default void mkString(PrettyPrinter pp) {
		switch(this) {
		case NoExpectation() -> {
			pp.append("NO EXPECTATION");
		}
		case FromContext(Context ctx, _) -> {
			pp.append(ctx);
		}
		case FromAnnotation(Id name, Type type) -> {
			pp.append(name).append(":").append(type);
		}
		}
	}
}
