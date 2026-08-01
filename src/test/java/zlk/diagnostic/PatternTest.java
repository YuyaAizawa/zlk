package zlk.diagnostic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import zlk.compiler.Driver;
import zlk.util.collection.Seq;

public class PatternTest {
	@Test
	void incompleteBoolCaseReportsMissingConstructor() {
		String src = """
		toInt b =
		  case b of
		    True -> 1
		""";

		Diagnostic.IncompletePattern incomplete = onlyIncomplete(src);
		Seq<PatternWitness> witness = incomplete.examples();
		assertEquals(1, witness.size());
		assertCtor(witness.head(), "Basic.False", 0);
		assertEquals(3, incomplete.location().startLine());
		assertEquals(4, incomplete.location().endLine());
	}

	@Test
	void wildcardMakesLaterConstructorBranchRedundant() {
		String src = """
		toInt b =
		  case b of
		    _ -> 0
		    True -> 1
		""";

		Diagnostic.RedundantPattern redundant = assertInstanceOf(
				Diagnostic.RedundantPattern.class, onlyDiagnostic(src));
		assertEquals(3, redundant.overallLocation().startLine());
		assertEquals(5, redundant.overallLocation().endLine());
		assertEquals(5, redundant.patternLocation().startLine());
		assertEquals(5, redundant.patternLocation().endLine());
		assertEquals(1, redundant.caseIndex());
	}

	@Test
	void incompleteUserDefinedListCaseReportsSingleConsWitness() {
		String src = """
		type List a =
		  | Nil
		  | Cons a (List a)

		length xs =
		  case xs of
		    Nil -> 0
		""";

		Diagnostic.IncompletePattern incomplete = onlyIncomplete(src);
		Seq<PatternWitness> witness = incomplete.examples();
		assertEquals(1, witness.size());
		PatternWitness.Ctor cons = assertCtor(witness.head(), "Main.List.Cons", 2);
		assertTrue(cons.args().at(0) instanceof PatternWitness.Anything);
		assertTrue(cons.args().at(1) instanceof PatternWitness.Anything);
	}

	@Test
	void nestedBranchAfterGeneralConsIsRedundant() {
		String src = """
		type List a =
		  | Nil
		  | Cons a (List a)

		classify xs =
		  case xs of
		    Nil -> 0
		    Cons _ _ -> 1
		    Cons _ Nil -> 2
		""";

		assertInstanceOf(Diagnostic.RedundantPattern.class, onlyDiagnostic(src));
	}

	@Test
	void nestedBoolInsideMaybeReportsMissingNestedConstructor() {
		String src = """
		type Maybe a =
		  | Nothing
		  | Just a

		toInt m =
		  case m of
		    Nothing -> 0
		    Just True -> 1
		""";

		Diagnostic.IncompletePattern incomplete = onlyIncomplete(src);
		Seq<PatternWitness> witness = incomplete.examples();
		assertEquals(1, witness.size());

		PatternWitness.Ctor just = assertCtor(witness.head(), "Main.Maybe.Just", 1);
		assertCtor(just.args().head(), "Basic.False", 0);
	}

	@Test
	void emptyRecordPatternOnOpenRowIsIrrefutableAndMakesLaterRecordBranchRedundant() {
		String src = """
			classify record =
			  case record of
			    {} -> 0
			    { flag = True } -> 1
			""";

		assertInstanceOf(Diagnostic.RedundantPattern.class, onlyDiagnostic(src));
	}

	@Test
	void openRecordWithNestedMaybeBoolReportsMissingJustFalse() {
		String src = """
			type Maybe a =
			  | Nothing
			  | Just a
			classify record =
			  case record of
			    { value = Nothing } -> 0
			    { value = Just True } -> 1
			""";

		Diagnostic.IncompletePattern incomplete = onlyIncomplete(src);
		Seq<PatternWitness> witness = incomplete.examples();
		assertEquals(1, witness.size());

		PatternWitness.Record product = assertInstanceOf(PatternWitness.Record.class, witness.head());
		assertEquals(1, product.fields().size());
		assertEquals("value", product.fields().head().name());
		PatternWitness.Ctor just = assertCtor(product.fields().head().pattern(), "Main.Maybe.Just", 1);
		assertCtor(just.args().head(), "Basic.False", 0);
	}

	private static Diagnostic onlyDiagnostic(String src) {
		Driver.CompilationResult result = Driver.compile(
				"Main.zlk",
				"module Main\n" + src
		);
		Driver.CompilationResult.Failed failed = assertInstanceOf(Driver.CompilationResult.Failed.class, result);
		assertEquals(1, failed.diags().size());
		Diagnostic diagnostic = failed.diags().head();
		assertTrue(diagnostic.isError());
		return diagnostic;
	}

	private static Diagnostic.IncompletePattern onlyIncomplete(String src) {
		return assertInstanceOf(Diagnostic.IncompletePattern.class, onlyDiagnostic(src));
	}

	private static PatternWitness.Ctor assertCtor(
			PatternWitness pattern,
			String expectedCtor,
			int expectedArity
	) {
		assertTrue(pattern instanceof PatternWitness.Ctor, () -> "expected ctor pattern, but got: " + pattern);
		PatternWitness.Ctor ctor = (PatternWitness.Ctor) pattern;
		assertEquals(expectedCtor, ctor.name());
		assertEquals(expectedArity, ctor.args().size());
		return ctor;
	}
}
