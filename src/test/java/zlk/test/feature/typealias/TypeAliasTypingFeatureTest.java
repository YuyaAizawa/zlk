package zlk.test.feature.typealias;

import org.junit.jupiter.api.Test;

import zlk.util.fixture.CompilationFixture;

public class TypeAliasTypingFeatureTest {
	@Test
	void aliasExpandsEmptyRecordArgumentToClosedRow() {
		var module = CompilationFixture.compileSucceeded(
				"""
				type alias Foo a = { a | x : I32, y : Bool }
				closed : Foo {}
				closed = { x = 1, y = True }
				""");

		module.assertType("closed", "{ x : I32, y : Bool }");
	}

	@Test
	void aliasExpandsWiderClosedRecordArgument() {
		var module = CompilationFixture.compileSucceeded(
				"""
				type alias Foo a = { a | x : I32, y : Bool }
				wider : Foo { z : I32 }
				wider = { x = 1, y = True, z = 2 }
				""");

		module.assertType("wider", "{ x : I32, y : Bool, z : I32 }");
	}

	@Test
	void plainTypeAliasExpandsInValueAnnotation() {
		var module = CompilationFixture.compileSucceeded(
				"""
				type alias Box a = { value : a }
				box : Box I32
				box = { value = 1 }
				""");

		module.assertType("box", "{ value : I32 }");
	}

	@Test
	void aliasAllowsForwardReference() {
		var module = CompilationFixture.compileSucceeded(
				"""
				type alias B = A I32
				type A a = A0 a
				zero : B
				zero = A0 0
				""");

		module.assertType("zero", "Main.A I32");
	}

	@Test
	void aliasAllowsForwardReferenceToAnotherAlias() {
		var module = CompilationFixture.compileSucceeded(
				"""
				type alias B a = A a
				type alias A a = { value : a }
				box : B I32
				box = { value = 1 }
				""");

		module.assertType("box", "{ value : I32 }");
	}

	@Test
	void functionAliasWithMultipleRowParametersAcceptsSameRowForInputAndOutput() {
		var module = CompilationFixture.compileSucceeded(
				"""
				type alias Mapper inputRow outputRow a b = { inputRow | value : a } -> { outputRow | value : b }
				sameRow : Mapper r r I32 I32
				sameRow record = { record | value = 2 }
				sameResult = sameRow { x = 1, value = 5 }
				""");

		module.assertType("sameRow", "{ r | value : I32 } -> { r | value : I32 }");
		module.assertType("sameResult", "{ value : I32, x : I32 }");
	}

	@Test
	void functionAliasWithMultipleRowParametersAcceptsDistinctRowsForInputAndOutput() {
		var module = CompilationFixture.compileSucceeded(
				"""
				type alias Mapper inputRow outputRow a b = { inputRow | value : a } -> { outputRow | value : b }
				diffRow : Mapper { x : I32 } { y : Bool } I32 Bool
				diffRow record = { y = True, value = True }
				diffResult = diffRow { x = 1, value = 5 }
				""");

		module.assertType(
				"diffRow",
				"{ value : I32, x : I32 } -> { value : Bool, y : Bool }");
		module.assertType("diffResult", "{ value : Bool, y : Bool }");
	}
}
