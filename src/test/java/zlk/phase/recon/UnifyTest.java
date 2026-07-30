package zlk.phase.recon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import zlk.recon.FreshFlex;
import zlk.recon.Mismatch;
import zlk.recon.Unify;
import zlk.recon.Variable;

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

		assertThrows(Mismatch.class, () -> Unify.unify(a, b));
	}
}
