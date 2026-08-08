package zlk.test.feature.pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import zlk.util.fixture.CompilationFixture;
import zlk.util.tester.DumpOnFailureWatcher;

@ExtendWith(DumpOnFailureWatcher.class)
public class PatternFeatureTest {
	@Test
	void bindsMultipleRecordFields() {
		var module = CompilationFixture.compileSucceeded(
				"""
				extract { outer, tag } = outer.value
				result = extract { tag = True, outer = { value = 42, flag = False } }
				""");

		module.assertType("extract", "{ d | outer : { b | value : a }, tag : c } -> a");
		module.assertType("result", "I32");
		int actual = (int) module.value("result");
		assertEquals(42, actual);
	}

	@Test
	void bindsRecordFieldsInCaseBranch() {
		var module = CompilationFixture.compileSucceeded(
				"""
				read record =
				  case record of
				    { value } -> value
				result = read { value = 1, ignored = True }
				""");

		int result = (int) module.value("result");
		assertEquals(1, result);
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
	void bindsRecordFieldsNestedInsideConstructors() {
		var module = CompilationFixture.compileSucceeded(
				"""
				type Box a = Box a
				unboxArg (Box { value }) = value
				unboxCase box =
				  case box of
				    Box { value } -> value
				argResult = unboxArg (Box { value = 4, ignored = True })
				caseResult = unboxCase (Box { value = 5, ignored = False })
				""");

		assertEquals(4, (int) module.value("argResult"));
		assertEquals(5, (int) module.value("caseResult"));
	}

}
