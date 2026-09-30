package zlk.ir.reuse.own;

import java.util.function.Consumer;

import zlk.common.ConstValue;
import zlk.common.id.Id;
import zlk.util.collection.Seq;

/**
 * Own IRのBind右辺．変数出現はすべてOwnUseとして利用方法を保持する．
 */
public sealed interface OwnRhs {

	record Cnst(ConstValue value) implements OwnRhs {}

	record DirectApp(Id fun, Seq<OwnUse> args) implements OwnRhs {}

	record ClosureApp(OwnUse fun, Seq<OwnUse> args) implements OwnRhs {}

	record MakeClosure(Id impl, Seq<OwnUse> captures) implements OwnRhs {}

	record If(OwnUse condition, OwnBlock thenBlock, OwnBlock elseBlock) implements OwnRhs {}

	record Case(OwnUse target, Seq<OwnBranch> branches) implements OwnRhs {}

	record MakeRecord(Seq<OwnRecordField> fields) implements OwnRhs {}

	record RecordGet(OwnUse target, String field) implements OwnRhs {}

	record RecordUpdate(OwnUse target, Seq<OwnRecordField> fields) implements OwnRhs {}



	record OwnRecordField(String name, OwnUse value) {}

	public default void walkDirectUses(Consumer<OwnUse> action) {
		switch(this) {
		case OwnRhs.Cnst _:
			break;
		case OwnRhs.DirectApp(_, Seq<OwnUse> args):
			args.forEach(action);
			break;
		case OwnRhs.ClosureApp(OwnUse fun, Seq<OwnUse> args):
			action.accept(fun);
			args.forEach(action);
			break;
		case OwnRhs.MakeClosure(_, Seq<OwnUse> captures):
			captures.forEach(action);
			break;
		case OwnRhs.If(OwnUse condition, _, _):
			action.accept(condition);
			break;
		case OwnRhs.Case(OwnUse target, _):
			action.accept(target);
			break;
		case OwnRhs.MakeRecord(Seq<OwnRecordField> fields):
			fields.forEach(field -> action.accept(field.value));
			break;
		case OwnRhs.RecordGet(OwnUse target, _):
			action.accept(target);
			break;
		case OwnRhs.RecordUpdate(OwnUse target, Seq<OwnRecordField> fields):
			action.accept(target);
			fields.forEach(field -> action.accept(field.value));
			break;
		}
	}
}
