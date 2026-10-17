package zlk.test.feature.typealias;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import zlk.util.fixture.CompilationFixture;

public class TypeAliasTypingFeatureTest {
	@Test
	void equivalentAliasesUseDeclarationOrderRatherThanAnnotationsOrConstructors() {
		var module = CompilationFixture.compileSucceeded(
				"""
				type alias XY = { x : I32, y : Bool }
				type alias YX = { y : Bool, x : I32 }
				original = YX True 10
				annotated : XY
				annotated = original
				literal : YX
				literal = { x = 20, y = False }
				lambda : I32 -> YX
				lambda = \\x -> YX True x
				identity : YX -> { x : I32, y : Bool }
				identity value = value
				result = identity (lambda 10)
				""");
		for (String declaration : java.util.List.of("original", "annotated", "literal", "result"))
			assertEquals("Main.XY", displayed(module, declaration));
		assertEquals("I32 -> Main.XY", displayed(module, "lambda"));
		assertEquals("Main.XY -> Main.XY", displayed(module, "identity"));
		assertEquals("Bool -> I32 -> Main.XY", displayed(module, "YX"));
	}

	@Test
	void nestedEquivalentAliasesUseDeclarationOrderIndependentlyOfSharedValueAnnotations() {
		var module = CompilationFixture.compileSucceeded(
				"""
				type alias XY = { x : I32, y : Bool }
				type alias YX = { y : Bool, x : I32 }
				original = { nested = YX True 10, flag = False }
				annotated : { nested : YX, flag : Bool }
				annotated = original
				""");
		assertEquals("{ flag : Bool, nested : Main.XY }", displayed(module, "original"));
		assertEquals("{ flag : Bool, nested : Main.XY }", displayed(module, "annotated"));
	}

	@Test
	void inferredTypeOutputSelectsConcreteAliasWithoutChangingTransparentType() {
		var module = CompilationFixture.compileSucceeded("""
				type alias XY = { y : I32, x : I32 }
				make value = { x = value, y = value }
				make10 = make 10
				""");
		module.assertType("make", "a -> { x : a, y : a }");
		module.assertType("make10", "{ x : I32, y : I32 }");
		assertEquals("Main.XY", module.inferredTypes().filter(info -> info.declaration().simpleName().equals("make10")).head().renderedType());
		assertEquals("a -> { x : a, y : a }", module.inferredTypes().filter(info -> info.declaration().simpleName().equals("make")).head().renderedType());
	}

	private static String displayed(CompilationFixture module, String declaration) {
		return module.inferredTypes().filter(info -> info.declaration().simpleName().equals(declaration))
				.head().renderedType();
	}

	@Test
	void genericAndConcreteTypesSelectStrictSubsetsBeforeDeclarationOrder() {
		var module = CompilationFixture.compileSucceeded("""
				type alias Pair a b = { first : a, second : b }
				type alias Same a = { second : a, first : a }
				type alias Ints = { second : I32, first : I32 }
				make value = { first = value, second = value }
				integer = make 10
				boolean = make True
				""");
		assertEquals("a -> Main.Same a", displayed(module, "make"));
		assertEquals("Main.Ints", displayed(module, "integer"));
		assertEquals("Main.Same Bool", displayed(module, "boolean"));
		module.assertType("make", "a -> { first : a, second : a }");
	}

	@Test
	void multipleTypeAndRowArgumentsComeFromTheMatchedTermType() {
		var module = CompilationFixture.compileSucceeded("""
				type alias Mapper inputRow outputRow a b = { inputRow | value : a } -> { outputRow | value : b }
				same : Mapper r r I32 I32
				same record = { record | value = 2 }
				different : Mapper { x : I32 } { y : Bool } I32 Bool
				different record = { y = True, value = True }
				""");
		assertEquals("Main.Mapper r r I32 I32", displayed(module, "same"));
		assertEquals("Main.Mapper { x : I32 } { y : Bool } I32 Bool", displayed(module, "different"));
	}

	@Test
	void chainsUseSourceOrderAndNestedArgumentsUseApplicationPrecedence() {
		var module = CompilationFixture.compileSucceeded("""
				type alias First a = Last a
				type alias Last a = { value : a }
				type alias Number = I32
				type Box a = Box a
				identity value = value
				nested = { value = { value = 10 } }
				function = { value = identity }
				nominal = Box { value = 10 }
				""");
		assertEquals("Main.First (Main.First Main.Number)", displayed(module, "nested"));
		assertEquals("Main.First (a -> a)", displayed(module, "function"));
		assertEquals("Main.Box (Main.First Main.Number)", displayed(module, "nominal"));
	}

	@Test
	void phantomParametersRemainFreshAndIdentityAliasesTerminate() {
		var phantom = CompilationFixture.compileSucceeded("""
				type alias Phantom unused = { x : I32 }
				value = { x = 10 }
				""");
		assertEquals("Main.Phantom a", displayed(phantom, "value"));
		var identity = CompilationFixture.compileSucceeded("""
				type alias Identity a = a
				value = 10
				""");
		assertEquals("Main.Identity I32", displayed(identity, "value"));

	}

	@Test
	void phantomAliasArgumentsPreserveArityInsideNominalTypesAndArrows() {
		var module = CompilationFixture.compileSucceeded("""
				type Custom a = Custom I32
				type alias Phantom unused = I32
				value : Custom a
				value = Custom 10
				function : a -> I32
				function ignored = 10
				""");
		assertEquals("Main.Custom a", displayed(module, "value"));
		assertEquals("a -> Main.Phantom b", displayed(module, "function"));
		module.assertType("value", "Main.Custom a");
		module.assertType("function", "a -> I32");
	}

	@Test
	void incomparableCandidatesUseDeclarationOrderAfterAllDominatedCandidatesAreRemoved() {
		var module = CompilationFixture.compileSucceeded("""
				type alias General a b = { x : a, y : b }
				type alias Open r = { r | y : Bool }
				type alias Specific a = { x : I32, y : a }
				value = { x = 10, y = True }
				""");
		assertEquals("Main.Open { x : I32 }", displayed(module, "value"));
	}

	@Test
	void incomparableClosedAndOpenCandidatesFallBackToRegistrationOrder() {
		String closed = "type alias Closed a b = { x : a, y : b }\n";
		String open = "type alias Open r = { r | x : I32 }\n";
		var closedFirst = CompilationFixture.compileSucceeded(closed + open + "value = { x = 10, y = True }\n");
		var openFirst = CompilationFixture.compileSucceeded(open + closed + "value = { x = 10, y = True }\n");
		assertEquals("Main.Closed I32 Bool", displayed(closedFirst, "value"));
		assertEquals("Main.Open { y : Bool }", displayed(openFirst, "value"));
	}

	@Test
	void sharedRowParametersRequireTheSameRemainderAtEveryOccurrence() {
		var module = CompilationFixture.compileSucceeded("""
				type alias Shared r = { r | x : I32 } -> { r | y : Bool }
				same : { x : I32, z : I32 } -> { y : Bool, z : I32 }
				same record = { y = True, z = record.z }
				different : { x : I32, z : I32 } -> { y : Bool, z : Bool }
				different record = { y = True, z = False }
				""");
		assertEquals("Main.Shared { z : I32 }", displayed(module, "same"));
		assertEquals("{ x : I32, z : I32 } -> { y : Bool, z : Bool }", displayed(module, "different"));
	}

	@Test
	void inferredTypeReportingCannotChangeGeneratedBytes() {
		String source = """
				module Main
				type alias YX = { y : Bool, x : I32 }
				value = YX True 10
				""";
		var off = org.junit.jupiter.api.Assertions.assertInstanceOf(
				zlk.compiler.driver.Driver.CompilationResult.Succeeded.class,
				zlk.compiler.driver.Driver.compile("Main.zlk", source));
		var on = org.junit.jupiter.api.Assertions.assertInstanceOf(
				zlk.compiler.driver.Driver.CompilationResult.Succeeded.class,
				zlk.compiler.driver.Driver.compile("Main.zlk", source,
						zlk.compiler.CompilationOptions.DEFAULT.enable(zlk.compiler.CompilationOptions.Key.REPORT_INFERRED_TYPES)));
		assertEquals(off.clazzes().keySet(), on.clazzes().keySet());
		on.clazzes().forEach((name, bytes) ->
				org.junit.jupiter.api.Assertions.assertArrayEquals(off.clazzes().get(name), bytes));
	}

	@Test
	void constructorAcceptsFunctionAndNestedRecordFields() {
		var module = CompilationFixture.compileSucceeded(
				"""
				type alias Holder a = { transform : I32 -> I32, box : { value : a } }
				increment x = add x 1
				holder = Holder increment { value = 10 }
				result = holder.transform holder.box.value
				""");
		module.assertType("Holder", "(I32 -> I32) -> { value : a } -> { box : { value : a }, transform : I32 -> I32 }");
		module.assertType("holder", "{ box : { value : I32 }, transform : I32 -> I32 }");
		assertEquals(11, module.value("result"));
	}

	@Test
	void aliasExpandsEmptyRecordArgumentToClosedRow() {
		var module = CompilationFixture.compileSucceeded(
				"""
				type alias Foo a = { a | x : I32, y : Bool }
				closed : Foo {}
				closed = { x = 1, y = True }
				""");

		module.assertType("closed", "{ x : I32, y : Bool }");
		assertEquals("Main.Foo {}", displayed(module, "closed"));
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
