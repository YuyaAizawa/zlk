package zlk.ir.anf;

import zlk.common.id.Id;
import zlk.util.collection.Seq;

/**
 * 代入式の右辺
 *
 * 後段で右辺出現の変数を一部に定数になりえない位置もAnfAtomとしている
 */
public sealed interface AnfRhs {
	record Move(AnfAtom source) implements AnfRhs {
	}

	record DirectApp(Id fun, Seq<AnfAtom> args) implements AnfRhs {
	}

	record ClosureApp(AnfAtom fun, Seq<AnfAtom> args) implements AnfRhs {
	}

	record MakeClosure(Id impl, Seq<AnfAtom> captures) implements AnfRhs {
	}

	record If(AnfAtom condition, AnfBlock thenBlock, AnfBlock elseBlock) implements AnfRhs {
	}

	record Case(AnfAtom target, Seq<AnfBranch> branches) implements AnfRhs {
	}

	record MakeRecord(Seq<AnfRecordField> fields) implements AnfRhs {
	}

	record RecordGet(AnfAtom target, String field) implements AnfRhs {
	}

	record AnfRecordField(String name, AnfAtom value) {}
	record RecordUpdate(AnfAtom target, Seq<AnfRecordField> fields) implements AnfRhs {
	}
}
