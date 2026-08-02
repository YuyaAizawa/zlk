package zlk.phase.patterncheck;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import zlk.diagnostic.Diagnostic;
import zlk.tester.ModuleTester;
import zlk.tester.ModuleTester.CompileLevel;
import zlk.util.collection.Seq;

public class PatternCheckerTest {
	@Test
	void exhaustiveBoolCaseHasNoError() {
		String src =
				"""
				toInt b =
				  case b of
				    False -> 0
				    True -> 1
				""";

		var module = new ModuleTester(src, CompileLevel.PATTERN_CHECK);
		Seq<Diagnostic> errors = module.getPatternErrors();

		assertNoErrors(errors);
	}


	@Test
	void nestedListPatternsCanBeExhaustive() {
		String src =
				"""
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
		Seq<Diagnostic> errors = module.getPatternErrors();

		assertNoErrors(errors);
	}


	@Test
	void nestedBoolInsideMaybeCanBeExhaustive() {
		String src =
				"""
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
		Seq<Diagnostic> errors = module.getPatternErrors();

		assertNoErrors(errors);
	}


	// ===== open rowパターンマッチ回帰テスト群 =====

	@Test
	void openRecordBoolFieldCaseIsExhaustive() {
		String src =
				"""
				classify record =
				  case record of
				    { flag = False } -> 0
				    { flag = True } -> 1
				""";

		var module = new ModuleTester(src, CompileLevel.PATTERN_CHECK);
		Seq<Diagnostic> errors = module.getPatternErrors();

		assertNoErrors(errors);
	}


	@Test
	void openRecordWithNestedMaybeBoolCaseIsExhaustive() {
		String src =
				"""
				type Maybe a =
				  | Nothing
				  | Just a

				classify record =
				  case record of
				    { value = Nothing } -> 0
				    { value = Just False } -> 1
				    { value = Just True } -> 2
				""";

		var module = new ModuleTester(src, CompileLevel.PATTERN_CHECK);
		Seq<Diagnostic> errors = module.getPatternErrors();

		assertNoErrors(errors);
	}


	private static void assertNoErrors(Seq<Diagnostic> errors) {
		assertEquals(0, errors.size(), () -> errors.join(System.lineSeparator()));
	}

}
