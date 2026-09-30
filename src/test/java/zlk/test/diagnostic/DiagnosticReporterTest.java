package zlk.test.diagnostic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import zlk.common.Location;
import zlk.common.Type;
import zlk.common.id.Id;
import zlk.diagnostic.Diagnostic;
import zlk.diagnostic.DiagnosticReporter;

public class DiagnosticReporterTest {
	@Test
	void excludingRejectsOnlyTheSpecifiedDiagnosticType() {
		ArrayList<Diagnostic> reported = new ArrayList<>();
		DiagnosticReporter reporter = reported::add;
		DiagnosticReporter excludingBytecodeStmt =
				reporter.excluding(Diagnostic.BytecodeStmt.class);

		excludingBytecodeStmt.report(new Diagnostic.InferredType(
				Location.noLocation(), Id.intern("Main.answer"), Type.I32));
		excludingBytecodeStmt.report(new Diagnostic.BytecodeStmt(
				Location.noLocation(), Id.intern("Main.answer"), 0, Optional.empty()));

		assertEquals(1, reported.size());
		assertEquals(Diagnostic.InferredType.class, reported.getFirst().getClass());
	}
}