package zlk.test.phase.recon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import zlk.compiler.phase.recon.FreshFlex;
import zlk.compiler.phase.recon.Mismatch;
import zlk.compiler.phase.recon.Unify;
import zlk.compiler.phase.recon.Variable;

public class UnifyTest {
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
