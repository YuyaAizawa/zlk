package zlk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import zlk.common.id.Id;
import zlk.patterncheck.PcError;
import zlk.patterncheck.PcError.Incomplete;
import zlk.patterncheck.PcPattern;
import zlk.tester.ModuleTester;
import zlk.tester.ModuleTester.CompileLevel;
import zlk.util.collection.Seq;

public class PatternMatchTest {
	@Test
	void exhaustiveBoolCaseHasNoError() {
		String src = """
		toInt b =
		  case b of
		    False -> 0
		    True -> 1
		""";

		var module = new ModuleTester(src, CompileLevel.PATTERN_CHECK);
		Seq<PcError> errors = module.getPatternErrors();

		assertNoErrors(errors);
	}

	@Test
	void incompleteBoolCaseReportsMissingConstructor() {
		String src = """
		toInt b =
		  case b of
		    True -> 1
		""";

		var module = new ModuleTester(src, CompileLevel.PATTERN_CHECK);
		Seq<PcError> errors = module.getPatternErrors();

		assertEquals(1, errors.size());
		PcError.Incomplete incomplete = (Incomplete) errors.head();

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

		var module = new ModuleTester(src, CompileLevel.PATTERN_CHECK);
		Seq<PcError> errors = module.getPatternErrors();

		assertEquals(1, errors.size());
		assertTrue(errors.head() instanceof PcError.Redundant);
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

		var module = new ModuleTester(src, CompileLevel.PATTERN_CHECK);
		Seq<PcError> errors = module.getPatternErrors();

		assertEquals(1, errors.size());
		PcError.Incomplete incomplete = (Incomplete) errors.head();

		Seq<PcPattern> witness = incomplete.examples();
		assertEquals(1, witness.size());
		PcPattern.Ctor cons = assertCtor(witness.head(), "Main.List", "Main.List.Cons", 2);
		assertTrue(cons.args().at(0) instanceof PcPattern.Anything);
		assertTrue(cons.args().at(1) instanceof PcPattern.Anything);
	}

	@Test
	void nestedListPatternsCanBeExhaustive() {
		String src = """
		type List a =
		  | Nil
		  | Cons a (List a)

		classify xs =
		  case xs of
		    Nil -> 0
		    Cons _ Nil -> 1
		    Cons _ (Cons _ rest) -> 2
		""";

		var module = new ModuleTester(src, CompileLevel.PATTERN_CHECK);
		Seq<PcError> errors = module.getPatternErrors();

		assertNoErrors(errors);
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

		var module = new ModuleTester(src, CompileLevel.PATTERN_CHECK);
		Seq<PcError> errors = module.getPatternErrors();

		assertEquals(1, errors.size());
		assertTrue(errors.head() instanceof PcError.Redundant);
	}

	@Test
	void nestedBoolInsideMaybeCanBeExhaustive() {
		String src = """
		type Maybe a =
		  | Nothing
		  | Just a

		toInt m =
		  case m of
		    Nothing -> 0
		    Just False -> 1
		    Just True -> 2
		""";

		var module = new ModuleTester(src, CompileLevel.PATTERN_CHECK);
		Seq<PcError> errors = module.getPatternErrors();

		assertNoErrors(errors);
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

		var module = new ModuleTester(src, CompileLevel.PATTERN_CHECK);
		Seq<PcError> errors = module.getPatternErrors();

		assertEquals(1, errors.size());
		PcError.Incomplete incomplete = (Incomplete) errors.head();

		Seq<PcPattern> witness = incomplete.examples();
		assertEquals(1, witness.size());

		PcPattern.Ctor just = assertCtor(witness.head(), "Main.Maybe", "Main.Maybe.Just", 1);
		assertCtor(just.args().head(), "Bool", "Basic.False", 0);
	}

	private static void assertNoErrors(Seq<PcError> errors) {
		assertEquals(0, errors.size(), () -> errors.join(System.lineSeparator()));
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
