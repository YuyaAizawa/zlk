package zlk.test.diagnostic;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import zlk.compiler.diagnostic.Diagnostic;
import zlk.compiler.diagnostic.PatternWitness;
import zlk.compiler.driver.Driver;
import zlk.compiler.id.Id;
import zlk.util.collection.Seq;
import zlk.util.fixture.CompilationFixture;

public class PatternTest {
	@Test
	void differentConstructorFamiliesReportPatternDiagnostic() {
		Diagnostic.ConstructorFamilyMismatch mismatch = onlyConstructorFamilyMismatch(
				"""
				type Maybe a =
				  | Nothing
				  | Just a

				bad value =
				  case value of
				    True -> 1
				    Nothing -> 0
				""");

		assertEquals(Id.intern("Main.Maybe.Nothing"), mismatch.constructor());
		assertEquals(Id.intern("Main.Maybe"), mismatch.actualFamily());
		assertEquals(Id.intern("Bool"), mismatch.expectedFamily());
		assertEquals(9, mismatch.location().startLine());
		assertTrue(mismatch.isError());
	}

	@Test
	void constructorFamilyDifferentFromExplicitCaseTargetReportsPatternDiagnostic() {
		Diagnostic.ConstructorFamilyMismatch mismatch = onlyConstructorFamilyMismatch(
				"""
				type Maybe a =
				  | Nothing
				  | Just a

				bad : Maybe a -> I32
				bad value =
				  case value of
				    True -> 1
				""");

		assertEquals(Id.intern("Basic.True"), mismatch.constructor());
		assertEquals(Id.intern("Bool"), mismatch.actualFamily());
		assertEquals(Id.intern("Main.Maybe"), mismatch.expectedFamily());
		assertEquals(9, mismatch.location().startLine());
	}

	@Test
	void nestedConstructorFamilyMismatchReportsNestedPattern() {
		Diagnostic.ConstructorFamilyMismatch mismatch = onlyConstructorFamilyMismatch(
				"""
				type Maybe a =
				  | Nothing
				  | Just a

				type Box a = Box a

				bad value =
				  case value of
				    Box True -> 1
				    Box Nothing -> 0
				""");

		assertEquals(Id.intern("Main.Maybe.Nothing"), mismatch.constructor());
		assertEquals(Id.intern("Main.Maybe"), mismatch.actualFamily());
		assertEquals(Id.intern("Bool"), mismatch.expectedFamily());
		assertEquals(11, mismatch.location().startLine());
	}

	@Test
	void constructorFamilyMismatchInLaterArgumentReportsOffendingPattern() {
		Diagnostic.ConstructorFamilyMismatch mismatch = onlyConstructorFamilyMismatch(
				"""
				type Maybe a =
				  | Nothing
				  | Just a

				type Pair a b = Pair a b

				bad value =
				  case value of
				    Pair True Nothing -> 1
				    Pair False True -> 0
				""");

		assertEquals(Id.intern("Basic.True"), mismatch.constructor());
		assertEquals(Id.intern("Bool"), mismatch.actualFamily());
		assertEquals(Id.intern("Main.Maybe"), mismatch.expectedFamily());
		assertEquals(11, mismatch.location().startLine());
	}

	@Test
	void incompleteBoolCaseReportsMissingConstructor() {
		String src =
				"""
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
		String src =
				"""
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
		String src =
				"""
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
		String src =
				"""
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
		String src =
				"""
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
	void recordPatternIsIrrefutableAndMakesLaterRecordBranchRedundant() {
		String src =
				"""
				classify record =
				  case record of
				    { flag } -> 0
				    { flag } -> 1
				""";

		assertInstanceOf(Diagnostic.RedundantPattern.class, onlyDiagnostic(src));
	}


	private static Diagnostic onlyDiagnostic(String src) {
		Driver.CompilationResult result = Driver.compile(
				"Main.zlk",
				"module Main\n" + src
		);
		Driver.CompilationResult.Failed failed = assertInstanceOf(Driver.CompilationResult.Failed.class, result);

		Seq<Diagnostic> diags = failed.diags();
		assertEquals(1, diags.size(), "expected only ONE Diagnostic, but was: "+diags);
		Diagnostic diagnostic = diags.head();
		assertTrue(diagnostic.isError());
		return diagnostic;
	}

	private static Diagnostic.IncompletePattern onlyIncomplete(String src) {
		return assertInstanceOf(Diagnostic.IncompletePattern.class, onlyDiagnostic(src));
	}

	private static Diagnostic.ConstructorFamilyMismatch onlyConstructorFamilyMismatch(String src) {
		CompilationFixture module = assertDoesNotThrow(() -> CompilationFixture.compile(src));
		assertInstanceOf(Driver.CompilationResult.Failed.class, module.result());
		var diagnostics = module.diagnostics(Diagnostic.ConstructorFamilyMismatch.class);
		assertEquals(1, diagnostics.size());
		assertTrue(module.diagnostics(Diagnostic.TypeMismatch.class).isEmpty());
		Diagnostic.ConstructorFamilyMismatch mismatch = diagnostics.head();
		assertTrue(mismatch.isError());
		return mismatch;
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
