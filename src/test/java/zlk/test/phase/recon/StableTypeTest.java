package zlk.test.phase.recon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import zlk.compiler.ir.typing.RecordField;
import zlk.compiler.ir.typing.Type;
import zlk.util.collection.Seq;

public class StableTypeTest {
	@Test
	void rowAndRecordAreDistinctClasses() {
		Type.Row closedRow = new Type.Row(
				Seq.of(new RecordField<>("x", Type.I32)),
				Optional.empty());
		Type.Record record = new Type.Record(closedRow);

		assertInstanceOf(Type.Row.class,    closedRow);
		assertInstanceOf(Type.Record.class, record);
		assertFalse(Type.Record.class.isInstance(closedRow),
				"Row は Record とは別クラスであるべき");
		assertFalse(Type.Row.class.isInstance(record),
				"Record は Row とは別クラスであるべき");
	}

	@Test
	void rowVarIsNotAType() {
		Type.RowVar rowVar = new Type.RowVar("row");

		assertFalse(Type.class.isInstance(rowVar),
				"RowVar は Type を実装してはならない");
	}

	@Test
	void closedRowPrettyPrintIsCanonical() {
		Type.Row closedRow = new Type.Row(
				Seq.of(
					new RecordField<>("y", Type.I32),
					new RecordField<>("x", Type.BOOL)),
				Optional.empty());

		assertEquals("{ x : Bool, y : I32 }", Type.buildRowString(closedRow));
	}

	@Test
	void openRowPrettyPrintPlacesRowVarBeforeFields() {
		Type.RowVar rowVar = new Type.RowVar("row");
		Type.Record openRecord = new Type.Record(
				Seq.of(new RecordField<>("x", Type.I32)),
				Optional.of(rowVar));
		Type.Row openRow = openRecord.row();

		assertEquals("{ row | x : I32 }", Type.buildRowString(openRow));
	}

	@Test
	void recordTypeRejectsDuplicateLabelsEvenWhenFieldTypesDiffer() {
		assertThrows(IllegalArgumentException.class, () -> new Type.Record(
				Seq.of(
					new RecordField<>("x", Type.I32),
					new RecordField<>("x", Type.BOOL))));
	}
}
