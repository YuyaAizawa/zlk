package zlk.diagnostic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import zlk.compiler.Driver;

public class SyntaxTest {

	@Test
	void reportsSingleSyntaxErrorAndFailsCompilation() {
		Driver.CompilationResult result = Driver.compile("Main.zlk",
				"""
				module Main
				bad = ?
				""");

		Driver.CompilationResult.Failed failed = assertInstanceOf(Driver.CompilationResult.Failed.class, result);
		assertEquals(1, failed.diags().size());
		Diagnostic.SyntaxError error = assertInstanceOf(Diagnostic.SyntaxError.class, failed.diags().head());
		assertTrue(error.isError());
		assertEquals(2, error.location().startLine());
	}

	@Test
	void reportsSyntaxErrorsInSourceOrderAndFailsCompilation() {
		Driver.CompilationResult result = Driver.compile("Main.zlk",
				"""
				module Main
				a =
				  ?
				b =
				  ??
				c =
				  1
				""");

		Driver.CompilationResult.Failed failed = assertInstanceOf(Driver.CompilationResult.Failed.class, result);
		assertEquals(2, failed.diags().size());
		Diagnostic.SyntaxError first = assertInstanceOf(Diagnostic.SyntaxError.class, failed.diags().at(0));
		Diagnostic.SyntaxError second = assertInstanceOf(Diagnostic.SyntaxError.class, failed.diags().at(1));
		assertEquals(3, first.location().startLine());
		assertEquals(5, second.location().startLine());
	}
}
