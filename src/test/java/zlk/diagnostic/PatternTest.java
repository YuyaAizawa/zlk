package zlk.diagnostic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import zlk.common.id.Id;
import zlk.compiler.Driver;
import zlk.diagnostic.Diagnostic;
import zlk.phase.patterncheck.PcPattern;
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
		Seq<PcPattern> witness = incomplete.examples();
		assertEquals(1, witness.size());
		assertCtor(witness.head(), "Bool", "Basic.False", 0);
	}

	@Test
	void wildcardMakesLaterConstructorBranchRedundant() {
		String src = """
		toInt b =
		  case b of
		    _ -> 0
		    True -> 1
		""";

		assertInstanceOf(Diagnostic.RedundantPattern.class, onlyDiagnostic(src));
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
		Seq<PcPattern> witness = incomplete.examples();
		assertEquals(1, witness.size());
		PcPattern.Ctor cons = assertCtor(witness.head(), "Main.List", "Main.List.Cons", 2);
		assertTrue(cons.args().at(0) instanceof PcPattern.Anything);
		assertTrue(cons.args().at(1) instanceof PcPattern.Anything);
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
		Seq<PcPattern> witness = incomplete.examples();
		assertEquals(1, witness.size());

		PcPattern.Ctor just = assertCtor(witness.head(), "Main.Maybe", "Main.Maybe.Just", 1);
		assertCtor(just.args().head(), "Bool", "Basic.False", 0);
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
		Seq<PcPattern> witness = incomplete.examples();
		assertEquals(1, witness.size());

		PcPattern.Ctor product = assertCtor(witness.head(), "$record$5$value", "$record$5$value", 1);
		PcPattern.Ctor just = assertCtor(product.args().head(), "Main.Maybe", "Main.Maybe.Just", 1);
		assertCtor(just.args().head(), "Bool", "Basic.False", 0);
	}

	private static Diagnostic onlyDiagnostic(String src) {
		Driver.CompilationResult result = Driver.compile(
				"Main.zlk",
				"module Main\n" + src
		);
		Driver.CompilationResult.Failed failed = assertInstanceOf(Driver.CompilationResult.Failed.class, result);
		assertEquals(1, failed.diags().size());
		return failed.diags().head();
	}

	private static Diagnostic.IncompletePattern onlyIncomplete(String src) {
		return assertInstanceOf(Diagnostic.IncompletePattern.class, onlyDiagnostic(src));
	}

	private static PcPattern.Ctor assertCtor(
			PcPattern pattern,
			String expectedUnion,
			String expectedCtor,
			int expectedArity
	) {
		assertTrue(pattern instanceof PcPattern.Ctor, () -> "expected ctor pattern, but got: " + pattern);
		PcPattern.Ctor ctor = (PcPattern.Ctor) pattern;
		assertEquals(Id.intern(expectedUnion), ctor.unionId());
		assertEquals(Id.intern(expectedCtor), ctor.ctorId());
		assertEquals(expectedArity, ctor.args().size());
		return ctor;
	}
}
