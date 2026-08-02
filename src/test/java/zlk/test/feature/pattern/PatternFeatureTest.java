package zlk.test.feature.pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import zlk.util.fixture.CompilationFixture;
import zlk.util.tester.DumpOnFailureWatcher;

@ExtendWith(DumpOnFailureWatcher.class)
public class PatternFeatureTest {
	@Test
	void matchesNestedRecordPatterns() {
		var module = CompilationFixture.compileSucceeded(
				"""
				extract { outer = { flag = _, value = value }, tag = _ } = value
				result = extract { tag = True, outer = { value = 42, flag = False } }
				""");

		module.assertType("extract", "{ e | outer : { c | flag : a, value : b }, tag : d } -> b");
		module.assertType("result", "I32");
		int actual = (int) module.value("result");
		assertEquals(42, actual);
	}

	@Test
	void checksRefutablePatternsNestedInsideRecords() {
		var module = CompilationFixture.compileSucceeded(
				"""
				type Option a = None | Some a
				read record =
				  case record of
				    { outer = { value = None } } -> 0
				    { outer = { value = Some value } } -> value
				zero = read { outer = { value = None } }
				one = read { outer = { value = Some 1 } }
				""");

		int zero = (int) module.value("zero");
		int one = (int) module.value("one");
		assertEquals(0, zero);
		assertEquals(1, one);
	}

	@Test
	void emptyRecordPatternMatchesKnownNonEmptyRecord() {
		var module = CompilationFixture.compileSucceeded(
				"""
				ignore : { x : I32 } -> I32
				ignore {} = 1
				result = ignore { x = 0 }
				caseResult =
				  case { x = 0 } of
				    {} -> 2
				""");

		module.assertType("result", "I32");
		int result = (int) module.value("result");
		int caseResult = (int) module.value("caseResult");
		assertEquals(1, result);
		assertEquals(2, caseResult);
	}

	@Test
	void partialRecordPatternMatchesKnownWiderRecord() {
		var module = CompilationFixture.compileSucceeded(
				"""
				pick : { x : I32, y : Bool } -> I32
				pick { x } = x
				result = pick { y = True, x = 3 }
				""");

		module.assertType("result", "I32");
		int actual = (int) module.value("result");
		assertEquals(3, actual);
	}

	@Test
	void checksPartialRecordBranchesAgainstTheFullKnownShape() {
		var module = CompilationFixture.compileSucceeded(
				"""
				type Option a = None | Some a
				read : { x : Option I32, y : Bool } -> I32
				read record =
				  case record of
				    { x = None } -> 0
				    { x = Some value, y = _ } -> value
				zero = read { y = True, x = None }
				one = read { y = False, x = Some 1 }
				""");

		int zero = (int) module.value("zero");
		int one = (int) module.value("one");
		assertEquals(0, zero);
		assertEquals(1, one);
	}
}
