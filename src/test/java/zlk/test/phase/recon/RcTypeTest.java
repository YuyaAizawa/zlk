package zlk.test.phase.recon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import zlk.compiler.ir.typing.RecordField;
import zlk.compiler.ir.typing.Type;
import zlk.compiler.phase.recon.FreshFlex;
import zlk.compiler.phase.recon.RcType;
import zlk.compiler.phase.recon.Variable;
import zlk.compiler.phase.recon.RcType.Inst;
import zlk.compiler.phase.recon.RcType.RecordN;
import zlk.util.collection.Seq;

public class RcTypeTest {
	@Test
	void rcTypePreservesOpenTail() {
		Type.RowVar rv = new Type.RowVar("r");
		Type.Record openRec = new Type.Record(
				Seq.of(new RecordField<>("x", Type.I32)),
				Optional.of(rv));
		FreshFlex fresh = new FreshFlex();

		Inst inst = RcType.instantiate(openRec, fresh);

		assertTrue(inst.type() instanceof RecordN);
		RecordN recN = (RecordN) inst.type();
		assertTrue(recN.extension().isPresent());
		assertEquals(Variable.Kind.ROW, recN.extension().get().kind());
		Type result = recN.toType();
		assertTrue(result instanceof Type.Record);
		Type.Row resultRow = ((Type.Record) result).row();
		assertTrue(resultRow.extension().isPresent());
		assertEquals("r", resultRow.extension().get().name());
	}

	@Test
	void rcTypeRejectsSameNameAtDifferentKinds() {
		Type.Record rec = new Type.Record(
				Seq.of(new RecordField<>("x", new Type.Var("a"))),
				Optional.of(new Type.RowVar("a")));
		FreshFlex fresh = new FreshFlex();

		assertThrows(IllegalArgumentException.class,
				() -> RcType.instantiate(rec, fresh));
	}
}
