package zlk.compiler.ir.reuse.anf;

import zlk.compiler.id.Id;
import zlk.compiler.ir.ConstValue;
import zlk.compiler.ir.reuse.LocalVar;
import zlk.util.collection.Seq;

/**
 * 代入式の右辺
 *
 * 後段で右辺出現の変数を一部に定数になりえない位置もAnfAtomとしている
 */
public sealed interface AnfRhs {
	record Cnst(ConstValue value) implements AnfRhs {}

	record DirectApp(Id fun, Seq<LocalVar> args) implements AnfRhs {}

	record ClosureApp(LocalVar fun, Seq<LocalVar> args) implements AnfRhs {}

	record MakeClosure(Id impl, Seq<LocalVar> captures) implements AnfRhs {}

	record If(LocalVar condition, AnfBlock thenBlock, AnfBlock elseBlock) implements AnfRhs {}

	record Case(LocalVar target, Seq<AnfBranch> branches) implements AnfRhs {}

	record MakeRecord(Seq<AnfRecordField> fields) implements AnfRhs {}

	record RecordGet(LocalVar target, String field) implements AnfRhs {}

	record AnfRecordField(String name, LocalVar value) {}
	record RecordUpdate(LocalVar target, Seq<AnfRecordField> fields) implements AnfRhs {}
}
