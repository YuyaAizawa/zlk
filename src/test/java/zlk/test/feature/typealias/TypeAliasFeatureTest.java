package zlk.test.feature.typealias;

import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import zlk.runtime.ZlkRecord;
import zlk.util.fixture.CompilationFixture;
import zlk.util.tester.DumpOnFailureWatcher;

@ExtendWith(DumpOnFailureWatcher.class)
public class TypeAliasFeatureTest {
	@Test
	void constructorAndAnnotationNeverChangeCanonicalValueRendering() {
		var module = CompilationFixture.compileSucceeded("""
				type alias YX = { y : Bool, x : I32 }
				constructed = YX True 10
				annotated : YX
				annotated = { y = True, x = 10 }
				""");
		assertEquals("{ x = 10, y = True }", module.value("constructed").toString());
		assertEquals("{ x = 10, y = True }", module.value("annotated").toString());
	}

	@Test
	void onePolymorphicRecordKeepsCanonicalValueRenderingAtEveryUse() {
		var module = CompilationFixture.compileSucceeded("""
				type alias IntFns = { y : I32 -> I32, x : I32 -> I32 }
				probe ignored =
				  let
				    identity value = value
				    original = { x = identity, y = identity }
				    ints : IntFns
				    ints = original
				    bools : { x : Bool -> Bool, y : Bool -> Bool }
				    bools = original
				  in
				    { integers = ints, booleans = bools, generic = original }
				""");
		ZlkRecord result = (ZlkRecord) module.value("probe", 0);
		Object ints = result.get("integers");
		Object bools = result.get("booleans");
		Object generic = result.get("generic");
		org.junit.jupiter.api.Assertions.assertTrue(ints.toString().startsWith("{ x = "));
		org.junit.jupiter.api.Assertions.assertTrue(bools.toString().startsWith("{ x = "));
		org.junit.jupiter.api.Assertions.assertTrue(generic.toString().startsWith("{ x = "));
		assertEquals(ints, generic);
		assertEquals(bools, generic);
		assertEquals(ints.hashCode(), generic.hashCode());
	}

	@Test
	void concreteResultsKeepCanonicalValuesThroughControlFlowHigherOrderAndFieldOperations() {
		var module = CompilationFixture.compileSucceeded("""
				type alias XY = { y : I32, x : I32 }
				type Choice = Left | Right
				make value = { x = value, y = value }
				partial first second = { x = first, y = second }
				apply function value = function value
				choose flag left right = if flag then left else right
				fromCase flag =
				  case flag of
				    Left -> make 10
				    Right -> make 20
				fromLet value =
				  let
				    result = make value
				  in
				    result
				nested value = { inner = make value }
				intPartial = partial 10
				partialResult = intPartial 20
				higherResult = apply make 10
				ifResult = choose True (make 10) (make 20)
				caseResult = fromCase Left
				letResult = fromLet 10
				getResult = (nested 10).inner
				updateResult = { getResult | x = 20 }
				""");
		for (String declaration : java.util.List.of("higherResult", "ifResult", "caseResult", "letResult", "getResult")) {
			assertEquals("{ x = 10, y = 10 }", module.value(declaration).toString());
			assertEquals("Main.XY", module.inferredTypes().filter(info -> info.declaration().simpleName().equals(declaration)).head().renderedType());
		}
		assertEquals("{ x = 10, y = 20 }", module.value("partialResult").toString());
		assertEquals("{ x = 20, y = 10 }", module.value("updateResult").toString());
		module.assertType("make", "a -> { x : a, y : a }");
	}

	@Test
	void nestedConcreteCallAndGenericProducerKeepCanonicalValues() {
		var module = CompilationFixture.compileSucceeded("""
				type alias XY = { y : I32, x : I32 }
				make value = { nested = { x = value, y = value } }
				concrete = make 10
				""");
		assertEquals("{ nested = { x = 10, y = 10 } }", module.value("concrete").toString());
		assertEquals("{ nested : Main.XY }", module.inferredTypes().filter(info -> info.declaration().simpleName().equals("concrete")).head().renderedType());
		assertEquals("{ nested = { x = 10, y = 10 } }", module.value("make", 10).toString());
	}

	@Test
	void genericAndConcreteCallsDoNotChangeValueRendering() {
		var module = CompilationFixture.compileSucceeded("""
				type alias XY = { y : I32, x : I32 }
				make value = { x = value, y = value }
				intResult = make 10
				boolResult = make True
				""");
		module.assertType("make", "a -> { x : a, y : a }");
		assertEquals("{ x = 10, y = 10 }", module.value("make", 10).toString());
		assertEquals("{ x = 10, y = 10 }", module.value("intResult").toString());
		assertEquals("{ x = True, y = True }", module.value("boolResult").toString());
	}

	@Test
	void nestedAliasSyntaxDoesNotChangeValueRendering() {
		var module = CompilationFixture.compileSucceeded("""
				type alias XY = { x : I32, y : Bool }
				type alias YX = { y : Bool, x : I32 }
				type alias Holder = { nested : YX }
				holder = { nested = { x = 10, y = True } }
				""");
		ZlkRecord holder = (ZlkRecord) module.value("holder");
		assertEquals("{ x = 10, y = True }", holder.get("nested").toString());
	}

	@Test
	void functionFieldResultHasCanonicalValueRendering() {
		var module = CompilationFixture.compileSucceeded("""
				type alias XY = { x : I32, y : Bool }
				type alias YX = { y : Bool, x : I32 }
				type alias Holder = { make : I32 -> YX }
				holder = { make = \\value -> XY value True }
				result = holder.make 10
				""");
		assertEquals("{ x = 10, y = True }", module.value("result").toString());
	}

	@Test
	void functionFieldsRemainOrdinaryFunctionsThroughNestedRecordsAndCurriedResults() {
		var module = CompilationFixture.compileSucceeded("""
				type alias XY = { x : I32, y : Bool }
				type alias YX = { y : Bool, x : I32 }
				type alias F = I32 -> YX
				probe flag =
				  let
				    original x = XY x flag
				    shared = { nested = { make = original } }
				    annotated : { nested : { make : F } }
				    annotated = shared
				    returned : I32 -> { nested : { make : I32 -> F } }
				    returned ignored = { nested = { make = \\ignoredAgain -> original } }
				    apply : { make : F } -> { x : I32, y : Bool }
				    apply = \\{ make } -> make 10
				  in
				    { original = shared.nested.make 10, annotated = annotated.nested.make 10, returned = (returned 0).nested.make 0 10, argument = apply { make = original } }
				""");
		ZlkRecord result = (ZlkRecord) module.value("probe", true);
		assertEquals("{ x = 10, y = True }", result.get("original").toString());
		for (String field : java.util.List.of("annotated", "returned", "argument"))
			assertEquals("{ x = 10, y = True }", result.get(field).toString());
	}

	@Test
	void nestedFunctionFieldConstructorPatternDoesNotPreserveAliasIdentity() {
		var module = CompilationFixture.compileSucceeded("""
				type alias XY = { x : I32, y : Bool }
				type alias YX = { y : Bool, x : I32 }
				type Box a = Box a
				type alias F = Box YX -> { x : I32, y : Bool }
				holder : { nested : { make : F } }
				holder = { nested = { make = \\(Box value) -> value } }
				original = XY 10 True
				result = holder.nested.make (Box original)
				""");
		assertEquals("{ x = 10, y = True }", module.value("result").toString());
		assertEquals("{ x = 10, y = True }", module.value("original").toString());
	}

	@Test
	void recordPatternFunctionFieldUsesTransparentTypes() {
		var module = CompilationFixture.compileSucceeded("""
				type alias XY = { x : I32, y : Bool }
				type alias YX = { y : Bool, x : I32 }
				type alias F = I32 -> YX
				f : { make : F } -> { x : I32, y : Bool }
				f { make } = make 10
				original x = XY x True
				result = f { make = original }
				originalResult = original 10
				""");
		assertEquals("{ x = 10, y = True }", module.value("result").toString());
		assertEquals("{ x = 10, y = True }", module.value("originalResult").toString());
		module.assertType("f", "{ make : I32 -> { x : I32, y : Bool } } -> { x : I32, y : Bool }");
	}

	@Test
	void structuralRecordAnnotationDoesNotChangeFunctionCallValues() {
		var module = CompilationFixture.compileSucceeded("""
				type alias XY = { x : I32, y : Bool }
				type alias YX = { y : Bool, x : I32 }
				type alias F = I32 -> YX
				original x = XY x True
				holder : { make : F }
				holder = { make = original }
				result = holder.make 10
				originalResult = original 10
				""");
		assertEquals("{ x = 10, y = True }", module.value("result").toString());
		assertEquals("{ x = 10, y = True }", module.value("originalResult").toString());
	}

	@Test
	void transparentFunctionAliasesAndEtaFormsHaveIdenticalValueSemantics() {
		var module = CompilationFixture.compileSucceeded("""
				type alias XY = { x : I32, y : Bool }
				type alias YX = { y : Bool, x : I32 }
				type alias Function a b = a -> b
				type alias F = Function I32 YX
				original x = XY x True
				eta : F
				eta x = original x
				lambda : F
				lambda = \\x -> original x
				value : F
				value = original
				etaResult = eta 10
				lambdaResult = lambda 10
				valueResult = value 10
				originalResult = original 10
				""");
		for (String result : java.util.List.of("etaResult", "lambdaResult", "valueResult"))
			assertEquals("{ x = 10, y = True }", module.value(result).toString());
		assertEquals("{ x = 10, y = True }", module.value("originalResult").toString());
	}

	@Test
	void annotatedFunctionArgumentUsesOrdinaryCapturedFunction() {
		var module = CompilationFixture.compileSucceeded("""
				type alias XY = { x : I32, y : Bool }
				type alias YX = { y : Bool, x : I32 }
				type alias F = I32 -> YX
				apply : F -> { x : I32, y : Bool }
				apply make = make 10
				probe flag =
				  let
				    original x = XY x flag
				    annotated : F
				    annotated = original
				  in
				    { original = original 10, annotated = annotated 10, argument = apply original }
				""");
		ZlkRecord result = (ZlkRecord) module.value("probe", true);
		assertEquals("{ x = 10, y = True }", result.get("original").toString());
		assertEquals("{ x = 10, y = True }", result.get("annotated").toString());
		assertEquals("{ x = 10, y = True }", result.get("argument").toString());
	}

	@Test
	void lambdaConstructorPatternUsesTransparentStructuralType() {
		var module = CompilationFixture.compileSucceeded("""
				type alias XY = { x : I32, y : Bool }
				type alias YX = { y : Bool, x : I32 }
				type Box a = Box a
				f : Box YX -> { x : I32, y : Bool }
				f = \\(Box value) -> value
				result = f (Box (XY 10 True))
				""");
		assertEquals("{ x = 10, y = True }", module.value("result").toString());
	}

	@Test
	void constructorPatternArgumentUsesTransparentStructuralType() {
		var module = CompilationFixture.compileSucceeded("""
				type alias XY = { x : I32, y : Bool }
				type alias YX = { y : Bool, x : I32 }
				type Box a = Box a
				f : Box { nested : YX } -> { x : I32, y : Bool }
				f (Box { nested }) = nested
				result = f (Box { nested = XY 10 True })
				""");
		assertEquals("{ x = 10, y = True }", module.value("result").toString());
	}

	@Test
	void recordPatternArgumentUsesTransparentStructuralType() {
		var module = CompilationFixture.compileSucceeded("""
				type alias XY = { x : I32, y : Bool }
				type alias YX = { y : Bool, x : I32 }
				f : { nested : YX } -> { x : I32, y : Bool }
				f { nested } = nested
				result = f { nested = XY 10 True }
				""");
		assertEquals("{ x = 10, y = True }", module.value("result").toString());
	}

	@Test
	void repeatedParameterConstructorIsPolymorphicAndUsesDeclarationOrder() {
		var module = CompilationFixture.compileSucceeded("""
				type alias Same a = { second : a, first : a }
				integer = Same 10 20
				boolean = Same True False
				""");
		module.assertType("Same", "a -> a -> { first : a, second : a }");
		module.assertType("integer", "{ first : I32, second : I32 }");
		module.assertType("boolean", "{ first : Bool, second : Bool }");
		ZlkRecord integer = (ZlkRecord) module.value("integer");
		assertEquals(10, integer.get("second"));
		assertEquals(20, integer.get("first"));
		assertEquals("{ first = 20, second = 10 }", integer.toString());
		assertEquals("{ first = False, second = True }", module.value("boolean").toString());
	}

	@Test
	void constructorAcceptsPolymorphicEndomorphismField() {
		var module = CompilationFixture.compileSucceeded("""
				type alias Endo a = { transform : a -> a }
				identity x = x
				integer = (Endo identity).transform 10
				boolean = (Endo identity).transform True
				""");
		module.assertType("Endo", "(a -> a) -> { transform : a -> a }");
		module.assertType("integer", "I32");
		module.assertType("boolean", "Bool");
		assertEquals(10, module.value("integer"));
		assertEquals(true, module.value("boolean"));
	}

	@Test
	void differentAliasOrdersRemainStructurallyEqualToAnonymousRecords() {
		var module = CompilationFixture.compileSucceeded("""
				type alias XY = { x : I32, y : Bool }
				type alias YX = { y : Bool, x : I32 }
				xy = XY 10 True
				yx = YX True 10
				""");
		Object xy = module.value("xy");
		Object yx = module.value("yx");
		ZlkRecord anonymous = ZlkRecord.of(java.util.Map.of("x", 10, "y", true));
		assertEquals(xy, yx);
		assertEquals(yx, xy);
		assertEquals(anonymous, yx);
		assertEquals(yx, anonymous);
		assertEquals(anonymous.hashCode(), xy.hashCode());
		assertEquals(xy.hashCode(), yx.hashCode());
		assertFalse(yx.equals(anonymous.update("x", 20)));
	}

	@Test
	void openAliasAndNestedAnnotationsKeepCanonicalValues() {
		var module = CompilationFixture.compileSucceeded("""
				type alias XY = { x : I32, y : Bool }
				type alias YX = { y : Bool, x : I32 }
				type alias Outer r = { r | z : I32 }
				value : Outer { nested : YX }
				value = { nested = XY 10 True, z = 20 }
				""");
		assertEquals("{ nested = { x = 10, y = True }, z = 20 }", module.value("value").toString());
	}

	@Test
	void generalizedLiteralKeepsItsSharedTypeVariable() {
		var module = CompilationFixture.compileSucceeded("""
				type alias Pair a b = { first : a, second : b }
				type alias Shared r a = { r | second : a, first : a }
				type alias Same a = Shared {} a
				type alias IntPair = { first : I32, second : I32 }
				make value = { first = value, second = value }
				""");
		module.assertType("make", "a -> { first : a, second : a }");
		assertEquals("{ first = 10, second = 10 }", module.value("make", 10).toString());
		assertEquals("{ first = True, second = True }", module.value("make", true).toString());
	}

	@Test
	void constructorNestedFieldHasCanonicalValueRendering() {
		var module = CompilationFixture.compileSucceeded("""
				type alias XY = { x : I32, y : Bool }
				type alias YX = { y : Bool, x : I32 }
				type alias Outer = { nested : YX, flag : Bool }
				result = Outer (XY 10 True) False
				""");
		assertEquals("{ flag = False, nested = { x = 10, y = True } }", module.value("result").toString());
	}

	@Test
	void openAliasUpdateKeepsCanonicalValuesAndUnknownFields() {
		var module = CompilationFixture.compileSucceeded("""
				type alias Prefix r = { r | z : Bool, x : I32 }
				make : Prefix r -> Prefix r
				make value = { value | x = 20 }
				result = make { x = 10, z = True, b = False, a = 30 }
				""");
		assertEquals("{ a = 30, b = False, x = 20, z = True }", module.value("result").toString());
	}

	@Test
	void constructorFunctionAndLetBindingHaveTransparentValueSemantics() {
		var module = CompilationFixture.compileSucceeded("""
				type alias XY = { x : I32, y : Bool }
				type alias YX = { y : Bool, x : I32 }
				make value = YX True value
				annotated : XY
				annotated = make 10
				local value =
				  let
				    result : XY
				    result = YX False value
				  in
				    result
				""");
		assertEquals("{ x = 10, y = True }", module.value("annotated").toString());
		assertEquals("{ x = 20, y = False }", module.value("local", 20).toString());
	}

	@Test
	void updatesPreserveCanonicalValuesWithoutMutatingOtherBindings() {
		var module = CompilationFixture.compileSucceeded("""
				type alias XY = { x : I32, y : Bool }
				type alias YX = { y : Bool, x : I32 }
				probe value =
				  let
				    original = YX True value
				    alternate : XY
				    alternate = original
				    changed = { alternate | x = 20 }
				  in
				    { original = original, alternate = alternate, changed = changed }
				fresh value =
				  let
				    original : YX
				    original = { x = value, y = False }
				  in
				    { original | x = 30 }
				""");
		ZlkRecord result = (ZlkRecord) module.value("probe", 10);
		assertEquals("{ x = 10, y = True }", result.get("original").toString());
		assertEquals("{ x = 10, y = True }", result.get("alternate").toString());
		assertEquals("{ x = 20, y = True }", result.get("changed").toString());
		assertEquals("{ x = 30, y = False }", module.value("fresh", 10).toString());
	}

	@Test
	void structuralAnnotationDoesNotChangeNestedValueRendering() {
		var module = CompilationFixture.compileSucceeded("""
				type alias XY = { x : I32, y : Bool }
				type alias YX = { y : Bool, x : I32 }
				type alias Outer = { nested : { x : I32, y : Bool }, flag : Bool }
				annotated : { nested : YX, flag : Bool }
				annotated = { flag = False, nested = XY 10 True }
				""");
		assertEquals("{ flag = False, nested = { x = 10, y = True } }", module.value("annotated").toString());
	}

	@Test
	void aliasCandidatesDoNotChangeCanonicalValueRendering() {
		var module = CompilationFixture.compileSucceeded("""
				type alias Base r = { r | x : I32 }
				type alias YX = { y : I32, x : I32 }
				literal = { x = 10, y = 20 }
				""");
		assertEquals("{ x = 10, y = 20 }", module.value("literal").toString());
		assertEquals("Main.YX", module.inferredTypes().filter(info -> info.declaration().simpleName().equals("literal")).head().renderedType());
	}

	@Test
	void aliasConstructorSupportsPartialApplicationAndHigherOrderUse() {
		var module = CompilationFixture.compileSucceeded(
				"""
				type alias YX = { y : Bool, x : I32 }
				partial = YX True
				constructor = YX
				apply make = make False 20
				partialResult = partial 10
				functionResult = apply constructor
				""");
		module.assertType("partial", "I32 -> { x : I32, y : Bool }");
		module.assertType("constructor", "Bool -> I32 -> { x : I32, y : Bool }");
		ZlkRecord partial = (ZlkRecord) module.value("partialResult");
		assertEquals(true, partial.get("y"));
		assertEquals(10, partial.get("x"));
		assertEquals("{ x = 10, y = True }", partial.toString());
		ZlkRecord function = (ZlkRecord) module.value("functionResult");
		assertEquals(false, function.get("y"));
		assertEquals(20, function.get("x"));
		assertEquals("{ x = 20, y = False }", function.toString());
	}

	@Test
	void aliasConstructorCanBeReferencedBeforeItsDeclaration() {
		var module = CompilationFixture.compileSucceeded(
				"""
				r = YX True 10
				type alias YX = { y : Bool, x : I32 }
				""");
		ZlkRecord record = (ZlkRecord) module.value("r");
		assertEquals(true, record.get("y"));
		assertEquals(10, record.get("x"));
	}

	@Test
	void polymorphicAliasConstructorCanBeUsedAtDistinctTypes() {
		var module = CompilationFixture.compileSucceeded(
				"""
				type alias Box a = { value : a }
				integer = Box 10
				boolean = Box True
				""");
		module.assertType("Box", "a -> { value : a }");
		module.assertType("integer", "{ value : I32 }");
		module.assertType("boolean", "{ value : Bool }");
		assertEquals(10, ((ZlkRecord) module.value("integer")).get("value"));
		assertEquals(true, ((ZlkRecord) module.value("boolean")).get("value"));
	}

	@Test
	void emptyClosedRecordAliasIsAValue() {
		var module = CompilationFixture.compileSucceeded(
				"""
				type alias Empty = {}
				empty = Empty
				""");
		module.assertType("Empty", "{}");
		ZlkRecord record = (ZlkRecord) module.value("empty");
		assertEquals(java.util.List.of(), record.names());
		assertEquals(record, module.value("Empty"));
		assertEquals("{}", record.toString());
	}

	@Test
	void closedRecordAliasConstructorUsesDeclarationFieldOrder() {
		var module = CompilationFixture.compileSucceeded(
				"""
				type alias YX = { y : Bool, x : I32 }
				r = YX True 10
				""");

		module.assertType("YX", "Bool -> I32 -> { x : I32, y : Bool }");
		ZlkRecord record = (ZlkRecord) module.value("r");
		assertEquals(true, record.get("y"));
		assertEquals(10, record.get("x"));
		assertFalse(module.generatedClassNames().contains("Main$YX"));
	}

	@Test
	void aliasEndToEndResolvesToClosedRecordAndGeneratesNoAliasClass() {
		String src =
				"""
				type alias Foo a = { a | x : I32, y : Bool }
				bar : Foo { z : I32 } -> I32
				bar record = add record.x record.z
				result = bar { x = 1, y = True, z = 2 }
				""";

		var module = CompilationFixture.compileSucceeded(src);

		// bar resolved typeはclosed { x:I32, y:Bool, z:I32 } -> I32
		module.assertType("bar", "{ x : I32, y : Bool, z : I32 } -> I32");

		// result実行値3
		int result = (int) module.value("result");
		assertEquals(3, result);

		// alias用 Main$Foo classは生成されない
		assertFalse(module.generatedClassNames().contains("Main$Foo"));
	}
}
