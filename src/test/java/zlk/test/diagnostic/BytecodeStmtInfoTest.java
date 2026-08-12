package zlk.test.diagnostic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import zlk.common.id.Id;
import zlk.compiler.Driver;
import zlk.diagnostic.Diagnostic;
import zlk.util.collection.Seq;

public class BytecodeStmtInfoTest {
	@Test
	void doesNotReportByDefaultOrWhenDisabled() {
		String src = "module Main\nanswer = 42\n";

		Driver.CompilationResult defaultResult = Driver.compile("Main.zlk", src);
		Driver.CompilationResult disabledResult = Driver.compile(
				"Main.zlk", src, Driver.CompilationOptions.DEFAULT.reportBytecodeStmtOrder(false));

		assertEquals(0, diagnostics(defaultResult, Diagnostic.BytecodeStmt.class).size());
		assertEquals(0, diagnostics(disabledResult, Diagnostic.BytecodeStmt.class).size());
	}

	@Test
	void reportsRhsCompletionOrderIncludingStackTriggeredBindings() {
		String src = """
				module Main
				combine a b =
				  let
				    left = add a b
				  in
				    add left (sub a b)
				""";

		Driver.CompilationResult.Succeeded result = assertInstanceOf(
				Driver.CompilationResult.Succeeded.class,
				Driver.compile(
						"Main.zlk",
						src,
						Driver.CompilationOptions.DEFAULT.reportBytecodeStmtOrder(true)));
		Seq<Diagnostic.BytecodeStmt> stmts = result.diags()
				.filter(Diagnostic.BytecodeStmt.class::isInstance)
				.map(Diagnostic.BytecodeStmt.class::cast);

		assertEquals("2,3,4", stmts.map(stmt -> Integer.toString(stmt.localId())).join(","));
		assertEquals(Id.intern("Main.combine"), stmts.at(0).function());
		assertEquals(Id.intern("Main.combine"), stmts.at(1).function());
		assertEquals(Id.intern("Main.combine"), stmts.at(2).function());
		assertEquals(Optional.of(Id.intern("Main.combine.left")), stmts.at(0).variable());
		assertEquals(Optional.empty(), stmts.at(1).variable());
		assertEquals(Optional.empty(), stmts.at(2).variable());
		stmts.forEach(stmt -> assertEquals(Diagnostic.Severity.INFO, stmt.severity()));
	}

	@Test
	void reportsBytecodeStatementsAfterInferredTypes() {
		String src = "module Main\nanswer = 42\n";

		Driver.CompilationResult.Succeeded result = assertInstanceOf(
				Driver.CompilationResult.Succeeded.class,
				Driver.compile(
						"Main.zlk",
						src,
						Driver.CompilationOptions.DEFAULT
								.reportInferredTypes(true)
								.reportBytecodeStmtOrder(true)));

		assertEquals(1, diagnostics(result, Diagnostic.InferredType.class).size());
		assertEquals(1, diagnostics(result, Diagnostic.BytecodeStmt.class).size());
		assertEquals(Diagnostic.InferredType.class, result.diags().at(0).getClass());
		assertEquals(Diagnostic.BytecodeStmt.class, result.diags().at(1).getClass());
	}

	private static <T extends Diagnostic> Seq<T> diagnostics(
			Driver.CompilationResult result,
			Class<T> type
	) {
		return result.diags().filter(type::isInstance).map(type::cast);
	}
}
