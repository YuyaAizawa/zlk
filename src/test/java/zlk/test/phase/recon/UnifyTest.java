package zlk.test.phase.recon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import zlk.compiler.id.Id;
import zlk.compiler.ir.typing.RecordField;
import zlk.compiler.phase.recon.Content.Structure;
import zlk.compiler.phase.recon.FlatType;
import zlk.compiler.phase.recon.FreshFlex;
import zlk.compiler.phase.recon.Mismatch;
import zlk.compiler.phase.recon.Unify;
import zlk.compiler.phase.recon.Variable;
import zlk.util.collection.Seq;

public class UnifyTest {
	@Test
	void occursAcceptsSharedRecordFieldVariable() {
		Variable shared = Variable.ofRigid("a");
		Variable row = new Variable(new Structure(new FlatType.Row1(
				Seq.of(new RecordField<>("first", shared), new RecordField<>("second", shared)),
				Optional.empty())), 1, Variable.Kind.ROW);
		Variable record = new Variable(new Structure(new FlatType.Record1(row)), 1);
		assertFalse(record.occurs());
	}

	@Test
	void occursAcceptsSharedConstructorArgumentStructure() {
		Variable shared = new Variable(new Structure(new FlatType.Fun1(
				Variable.ofRigid("a"), Variable.ofRigid("b"))), 1);
		Variable pair = new Variable(new Structure(new FlatType.CtorApp1(
				Id.intern("Main.Pair"), Seq.of(shared, shared))), 1);
		assertFalse(pair.occurs());
	}

	@Test
	void occursRejectsCycleThroughUnifiedVariables() {
		FreshFlex fresh = new FreshFlex();
		Variable first = fresh.getVariable();
		Variable second = fresh.getVariable();
		Variable function = new Variable(new Structure(new FlatType.Fun1(second, Variable.ofRigid("a"))), 1);
		Unify.unify(first, second);
		Unify.unify(first, function);
		assertTrue(first.occurs());
		assertTrue(second.occurs());
		assertTrue(function.occurs());
	}

	@Test
	void occursRejectsCycleThroughRecordField() {
		FreshFlex fresh = new FreshFlex();
		Variable field = fresh.getVariable();
		Variable row = new Variable(new Structure(new FlatType.Row1(
				Seq.of(new RecordField<>("self", field)), Optional.empty())), 1, Variable.Kind.ROW);
		Variable record = new Variable(new Structure(new FlatType.Record1(row)), 1);
		Unify.unify(field, record);
		assertTrue(record.occurs());
		assertTrue(row.occurs());
	}

	@Test
	void unifyPreservesRowKind() {
		FreshFlex fresh = new FreshFlex();
		Variable a = fresh.getVariable(Variable.Kind.ROW);
		Variable b = fresh.getVariable(Variable.Kind.ROW);

		Unify.unify(a, b);

		assertEquals(Variable.Kind.ROW, a.kind());
		assertEquals(Variable.Kind.ROW, b.kind());
	}

	@Test
	void unifyRejectsDifferentKinds() {
		FreshFlex fresh = new FreshFlex();
		Variable a = fresh.getVariable(Variable.Kind.TYPE);
		Variable b = fresh.getVariable(Variable.Kind.ROW);

		Mismatch mismatch = assertThrows(Mismatch.class, () -> Unify.unify(a, b));

		assertEquals(Mismatch.Reason.KIND, mismatch.reason());
	}
}
