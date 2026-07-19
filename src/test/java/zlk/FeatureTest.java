package zlk;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import zlk.common.RecordField;
import zlk.common.Type;
import zlk.runtime.ZlkCustom;
import zlk.runtime.ZlkRecord;
import zlk.runtime.ZlkValue;
import zlk.tester.DumpOnFailureWatcher;
import zlk.tester.ModuleTester;
import zlk.tester.ModuleTester.CompileLevel;
import zlk.tester.ValueTester.VData;
import zlk.util.collection.Seq;

@ExtendWith(DumpOnFailureWatcher.class)
public class FeatureTest {
	@Test
	void emptyRecordLiteral() {
		var module = new ModuleTester("empty = {}", CompileLevel.BYTECODE_GEN);
		Object value = ((VData) module.getValue("empty")).value();

		assertTrue(value instanceof ZlkRecord);
		assertEquals("{}", value.toString());
	}

	@Test
	void recordLiteralUsesCanonicalFieldOrder() {
		var module = new ModuleTester(
				"record = { y = True, x = 1 }",
				CompileLevel.BYTECODE_GEN);
		ZlkRecord value = (ZlkRecord) ((VData) module.getValue("record")).value();

		assertEquals("{ x = 1, y = True }", value.toString());
		assertEquals(1, value.get("x"));
		assertEquals(true, value.get("y"));
	}

	@Test
	void instantiatesNestedRecords() {
		var module = new ModuleTester(
				"nested = { z = { y = True, x = 1 }, a = 2 }",
				CompileLevel.BYTECODE_GEN);
		ZlkRecord outer = (ZlkRecord) ((VData) module.getValue("nested")).value();
		ZlkRecord inner = (ZlkRecord) outer.get("z");

		assertEquals("{ a = 2, z = { x = 1, y = True } }", outer.toString());
		assertEquals(1, inner.get("x"));
		assertEquals(true, inner.get("y"));
	}

	@Test
	void infersNestedRecordsTogetherWithParametricPolymorphism() {
		var module = new ModuleTester("""
				wrap value = { payload = { value = value } }
				wrappedInt = wrap 1
				wrappedBool = wrap True
				""", CompileLevel.BYTECODE_GEN);
		Type.Var a = new Type.Var("a");
		Type.Record genericInner = new Type.Record(Seq.of(new RecordField<>("value", a)));
		Type.Record genericOuter = new Type.Record(Seq.of(new RecordField<>("payload", genericInner)));
		Type.Record intOuter = new Type.Record(Seq.of(new RecordField<>("payload",
				new Type.Record(Seq.of(new RecordField<>("value", Type.I32))))));
		Type.Record boolOuter = new Type.Record(Seq.of(new RecordField<>("payload",
				new Type.Record(Seq.of(new RecordField<>("value", Type.BOOL))))));

		module.getType("wrap").is(new Type.Arrow(a, genericOuter));
		module.getType("wrappedInt").is(intOuter);
		module.getType("wrappedBool").is(boolOuter);
		ZlkRecord intValue = (ZlkRecord) ((VData) module.getValue("wrappedInt")).value();
		ZlkRecord boolValue = (ZlkRecord) ((VData) module.getValue("wrappedBool")).value();
		assertEquals(1, ((ZlkRecord) intValue.get("payload")).get("value"));
		assertEquals(true, ((ZlkRecord) boolValue.get("payload")).get("value"));
	}

	@Test
	void matchesNestedRecordPatterns() {
		var module = new ModuleTester("""
				extract { outer = { flag = _, value = value }, tag = _ } = value
				result = extract { tag = True, outer = { value = 42, flag = False } }
				""", CompileLevel.BYTECODE_GEN);

		Type.Var a = new Type.Var("a");
		Type.Var b = new Type.Var("b");
		Type.Var c = new Type.Var("c");
		module.getType("extract").is(new Type.Arrow(
				new Type.Record(Seq.of(
						new RecordField<>("outer", new Type.Record(Seq.of(
								new RecordField<>("flag", a),
								new RecordField<>("value", b)))),
						new RecordField<>("tag", c))),
				b));
		module.getType("result").is(Type.I32);
		assertEquals(42, ((VData) module.getValue("result")).value());
	}

	@Test
	void checksRefutablePatternsNestedInsideRecords() {
		var module = new ModuleTester("""
				type Option a = None | Some a
				read record =
				  case record of
				    { outer = { value = None } } -> 0
				    { outer = { value = Some value } } -> value
				zero = read { outer = { value = None } }
				one = read { outer = { value = Some 1 } }
				""", CompileLevel.BYTECODE_GEN);

		assertEquals(0, ((VData) module.getValue("zero")).value());
		assertEquals(1, ((VData) module.getValue("one")).value());
	}

	@Test
	void accessesNestedFieldsAndUpdatesImmutably() {
		var module = new ModuleTester("""
				base = { y = True, x = 1 }
				updated = { base | x = 2 }
				nested = { outer = updated }
				selected = nested.outer.x
				baseX = base.x
				""", CompileLevel.BYTECODE_GEN);

		module.getType("selected").is(Type.I32);
		assertEquals(2, ((VData) module.getValue("selected")).value());
		assertEquals(1, ((VData) module.getValue("baseX")).value());
	}

	@Test
	void updatesMultipleRecordFieldsImmutably() {
		var module = new ModuleTester("""
				base = { z = 3, y = True, x = 1 }
				updated = { base | x = 2, y = False }
				baseX = base.x
				baseY = base.y
				updatedX = updated.x
				updatedY = updated.y
				updatedZ = updated.z
				""", CompileLevel.BYTECODE_GEN);

		assertEquals(1, ((VData) module.getValue("baseX")).value());
		assertEquals(true, ((VData) module.getValue("baseY")).value());
		assertEquals(2, ((VData) module.getValue("updatedX")).value());
		assertEquals(false, ((VData) module.getValue("updatedY")).value());
		assertEquals(3, ((VData) module.getValue("updatedZ")).value());
	}

	@Test
	void acceptsNestedRecordTypeAnnotations() {
		var module = new ModuleTester("""
				getValue : { outer : { value : I32 } } -> I32
				getValue record = record.outer.value
				result = getValue { outer = { value = 7 } }
				""", CompileLevel.BYTECODE_GEN);

		Type.Record argument = new Type.Record(Seq.of(new RecordField<>("outer",
				new Type.Record(Seq.of(new RecordField<>("value", Type.I32))))));
		module.getType("getValue").is(new Type.Arrow(argument, Type.I32));
		assertEquals(7, ((VData) module.getValue("result")).value());
	}

	@Test
	void distinguishesEmptyRecordTypeFromUnit() {
		var module = new ModuleTester("""
				empty : {}
				empty = {}
				""", CompileLevel.BYTECODE_GEN);

		module.getType("empty").is(new Type.Record(Seq.of()));
		assertEquals("{}", ((VData) module.getValue("empty")).value().toString());
	}

	@Test
	void emptyRecordPatternMatchesKnownNonEmptyRecord() {
		var module = new ModuleTester("""
				ignore : { x : I32 } -> I32
				ignore {} = 1
				result = ignore { x = 0 }
				caseResult =
				  case { x = 0 } of
				    {} -> 2
				""", CompileLevel.BYTECODE_GEN);

		module.getType("result").is(Type.I32);
		assertEquals(1, ((VData) module.getValue("result")).value());
		assertEquals(2, ((VData) module.getValue("caseResult")).value());
	}

	@Test
	void partialRecordPatternMatchesKnownWiderRecord() {
		var module = new ModuleTester("""
				pick : { x : I32, y : Bool } -> I32
				pick { x } = x
				result = pick { y = True, x = 3 }
				""", CompileLevel.BYTECODE_GEN);

		module.getType("result").is(Type.I32);
		assertEquals(3, ((VData) module.getValue("result")).value());
	}

	@Test
	void checksPartialRecordBranchesAgainstTheFullKnownShape() {
		var module = new ModuleTester("""
				type Option a = None | Some a
				read : { x : Option I32, y : Bool } -> I32
				read record =
				  case record of
				    { x = None } -> 0
				    { x = Some value, y = _ } -> value
				zero = read { y = True, x = None }
				one = read { y = False, x = Some 1 }
				""", CompileLevel.BYTECODE_GEN);

		assertTrue(module.getPatternErrors().isEmpty());
		assertEquals(0, ((VData) module.getValue("zero")).value());
		assertEquals(1, ((VData) module.getValue("one")).value());
	}

	@Test
	void preservesMemberAccessTargetPrecedenceWhenPrettyPrinting() {
		var module = new ModuleTester("value = (f x).y", CompileLevel.PARSE);

		assertTrue(module.getAst().buildString().contains("(f x).y"));
	}

	@Test
	void adtIsSealedInterfaceAndRecords() throws ReflectiveOperationException {
		String src = """
		type Option = None | Some I32 Bool

		none = None
		sameNone = None
		some = Some 1 True
		sameSome = Some 1 True
		otherSome = Some 2 False
		""";

		var module = new ModuleTester(src, CompileLevel.BYTECODE_GEN);
		Object none = ((VData) module.getValue("none")).value();
		Object sameNone = ((VData) module.getValue("sameNone")).value();
		Object some = ((VData) module.getValue("some")).value();
		Object sameSome = ((VData) module.getValue("sameSome")).value();
		Object otherSome = ((VData) module.getValue("otherSome")).value();

		Class<?> noneClass = none.getClass();
		Class<?> someClass = some.getClass();
		Class<?> optionClass = someClass.getInterfaces()[0];

		assertTrue(optionClass.isInterface());
		assertTrue(optionClass.isSealed());
		assertTrue(ZlkCustom.class.isAssignableFrom(optionClass));
		assertArrayEquals(
				new String[] { noneClass.getName(), someClass.getName() },
				Arrays.stream(optionClass.getPermittedSubclasses()).map(Class::getName).sorted().toArray(String[]::new));

		assertTrue(Modifier.isFinal(noneClass.getModifiers()));
		assertTrue(Modifier.isFinal(someClass.getModifiers()));
		assertTrue(noneClass.isRecord());
		assertTrue(someClass.isRecord());
		assertEquals(Record.class, noneClass.getSuperclass());
		assertEquals(Record.class, someClass.getSuperclass());
		assertEquals("Main", optionClass.getNestHost().getName());
		assertEquals("Main", noneClass.getNestHost().getName());
		assertEquals("Main", someClass.getNestHost().getName());

		assertEquals(0, noneClass.getRecordComponents().length);
		var someComponents = someClass.getRecordComponents();
		assertEquals(2, someComponents.length);
		assertEquals("val0", someComponents[0].getName());
		assertEquals(Integer.class, someComponents[0].getType());
		assertEquals(1, someComponents[0].getAccessor().invoke(some));
		assertEquals("val1", someComponents[1].getName());
		assertEquals(Boolean.class, someComponents[1].getType());
		assertEquals(true, someComponents[1].getAccessor().invoke(some));
		var someField = someClass.getDeclaredField("val0");
		assertTrue(Modifier.isPrivate(someField.getModifiers()));
		assertTrue(Modifier.isFinal(someField.getModifiers()));
		assertTrue(noneClass.getDeclaredMethod("appendStringTo", StringBuilder.class).isSynthetic());
		assertTrue(noneClass.getDeclaredMethod("appendStringAsArgTo", StringBuilder.class).isSynthetic());
		assertTrue(someClass.getDeclaredMethod("appendStringTo", StringBuilder.class).isSynthetic());
		assertTrue(someClass.getDeclaredMethod("appendStringAsArgTo", StringBuilder.class).isSynthetic());

		assertEquals(none, sameNone);
		assertEquals(none.hashCode(), sameNone.hashCode());
		assertEquals(some, sameSome);
		assertEquals(some.hashCode(), sameSome.hashCode());
		assertFalse(some.equals(otherSome));
		assertEquals("None", none.toString());
		assertEquals("Some 1 True", some.toString());
		assertEquals("Some 2 False", otherSome.toString());

		StringBuilder sb = new StringBuilder();
		someClass.getDeclaredMethod("appendStringTo", StringBuilder.class).invoke(some, sb);
		assertEquals("Some 1 True", sb.toString());
		sb.setLength(0);
		someClass.getDeclaredMethod("appendStringAsArgTo", StringBuilder.class).invoke(some, sb);
		assertEquals("(Some 1 True)", sb.toString());
		sb.setLength(0);
		noneClass.getDeclaredMethod("appendStringAsArgTo", StringBuilder.class).invoke(none, sb);
		assertEquals("None", sb.toString());
	}

	@Test
	void runtimeValueStringAppenderDispatches() {
		StringBuilder sb = new StringBuilder();
		ZlkValue.appendStringTo(sb, Integer.valueOf(2));
		ZlkValue.appendStringTo(sb.append(' '), Boolean.TRUE);
		ZlkValue.appendStringTo(sb.append(' '), "java");
		assertEquals("2 True java", sb.toString());
	}

	@Test
	void adtToStringPreservesZlkValues() {
		String src = """
		type Pair a b = Pair_ a b
		type List = Nil | Cons I32 List

		pair = Pair_ True False
		list = Cons 1 (Cons 2 Nil)
		""";

		var module = new ModuleTester(src, CompileLevel.BYTECODE_GEN);
		Object pair = ((VData) module.getValue("pair")).value();
		Object list = ((VData) module.getValue("list")).value();
		assertEquals("Pair_ True False", pair.toString());
		assertEquals("Cons 1 (Cons 2 Nil)", list.toString());
	}

	@Test
	void mutuallyReferentialCustomTypesLoad() {
		String src = """
		type A = A B | A0
		type B = B A | B0

		x = A (B (A B0))
		""";

		var module = new ModuleTester(src, CompileLevel.BYTECODE_GEN);
		Object x = ((VData) module.getValue("x")).value();
		assertEquals("A (B (A B0))", x.toString());
	}

	@Test
	void selfRecursiveFunction() {
		String src ="""
		fact n =
		  if isZero n then
		    1
		  else
		    let
		      one = 1
		      nn = sub n one
		    in
		      mul n (fact nn)
		""";

		var module = new ModuleTester(src, CompileLevel.BYTECODE_GEN);
		var fact = module.getValue("fact");
		fact.apply(0).is(1);
		fact.apply(5).is(120);
	}

	@Test
	void closuerConversion() {
		String src ="""
		make_adder x =
		  let
		    adder y =
		      let
		        adder2 z = add (add x y) z
		      in
		        adder2
		  in
		    adder
		""";
		var module = new ModuleTester(src, CompileLevel.BYTECODE_GEN);
		var make_adder = module.getValue("make_adder");
		make_adder.apply(1).apply(2).apply(3).is(6);
	}

	@Test
	void selfRecursiveAndClosure() {
		String src ="""
				f d n =
				  let
				    fuctplus m =
				      if isZero m then
				        1
				      else
				        add d (mul m (fuctplus (sub m 1)))
				  in
				    fuctplus n

				ans = f 1 3
				""";
		var module = new ModuleTester(src, CompileLevel.BYTECODE_GEN);
		module.getValue("ans").is(16);
	}

	@Test
	void enumDeclAndCaseExp() {
		String src="""
		type IntList = Nil | Cons I32 IntList

		sum list =
		  case list of
		    Nil -> 0
		    Cons hd tl -> add hd (sum tl)

		ans = sum (Cons 3 (Cons 2 (Cons 1 Nil)))
		""";
		var module = new ModuleTester(src, CompileLevel.BYTECODE_GEN);
		module.getValue("ans").is(6);
	}

	@Test
	void mutualRecursionAndClosure() {
		String src="""
		makeEvenFromOffset offset =
		  let
		    even n =
		      if isZero n then
		        True
		      else
		        odd (sub n 1)
		    odd n =
		      if isZero n then
		        False
		      else
		        even (sub n 1)
		    evenFromOffset n =
		      even (add n offset)
		  in
		    evenFromOffset
		main =
		  let
		    f = makeEvenFromOffset 1
		  in
		    f 3
		""";
		var module = new ModuleTester(src, CompileLevel.BYTECODE_GEN);
		module.getType("makeEvenFromOffset").is("I32 -> I32 -> Bool");
		module.getValue("main").is(true);
	}

	@Test
	void genericFunction() {
		String src="""
		type List a =
		  | Nil
		  | Cons a (List a)

		map f list=
		  case list of
		    Nil -> Nil
		    Cons e rest ->
		      Cons (f e) (map f rest)

		isZero_ i = isZero i

		test =
		  map isZero_ (Cons 0 (Cons 1 (Cons 2 Nil)))
		""";
		var module = new ModuleTester(src, CompileLevel.BYTECODE_GEN);
		module.getType("map").is("(a -> b) -> List a -> List b");
		module.getValue("test").isWrittenIn("Cons True (Cons False (Cons False Nil))");
	}

	@Test
	void pairType() {
		String src="""
		type Pair a b = Pair a b

		left pair =
		  case pair of
		    Pair a _ -> a

		right pair =
		  case pair of
		    Pair _ b -> b

		oneTrue = Pair 1 True

		oneTrueLeft = left oneTrue

		oneTrueRight = right oneTrue
		""";
		var module = new ModuleTester(src, CompileLevel.BYTECODE_GEN);
		module.getType("left").is("Pair a b -> a");
		module.getType("right").is("Pair a b -> b");
		module.getType("oneTrueLeft").is("I32");
		module.getType("oneTrueRight").is("Bool");
		module.getValue("oneTrueLeft").isWrittenIn("1");
		module.getValue("oneTrueRight").isWrittenIn("True");
	}

	@Test
	void genericAndClosure() {
		String src="""
		type Pair a b = Pair_ a b

		test =
		  let
		    id x = x
		    makePair a b =
		      Pair_ (id a) (id b)
		    intBoolPair = makePair 1 True
		  in
		    intBoolPair
		""";
		var module = new ModuleTester(src, CompileLevel.BYTECODE_GEN);
		module.getType("test").is("Pair I32 Bool");
		module.getValue("test").isWrittenIn("Pair_ 1 True");
	}

	@Test
	void leftPartialApplication() {
		String src="""
		fun x y = add x y
		f1 = fun 1

		type Pair a b = Pair_ a b
		f2 = Pair_ 1

		a1 = f1 2
		a2 = case f2 2 of
		  Pair_ a b -> add a b
		""";
		var module = new ModuleTester(src, CompileLevel.BYTECODE_GEN);
		module.getType("f1").is("I32 -> I32");
		module.getType("f2").is("a -> Pair I32 a");
		module.getValue("a1").is(3);
		module.getValue("a2").is(3);
	}

	@Test
	void minimumLambda() {
		String src ="""
		id =
		  \\x -> x
		apply =
		  \\f x -> f x
		add_ =
		  \\x y -> add x y
		ans =
		  (\\x -> add x 1) 2
		""";
		var module = new ModuleTester(src, CompileLevel.BYTECODE_GEN);
		module.getType("id").is("a -> a");
		module.getType("apply").is("(a -> b) -> a -> b");
		module.getType("add_").is("I32 -> I32 -> I32");
		module.getValue("ans").is(3);
	}

	@Test
	void mapAndUseTwice() {
		String src="""
		type List a = Nil | Cons a (List a)
		type Pair a b = Pair_ a b
		mapAndUseTwice =
		  let
		    map f xs =
		      case xs of
		        Nil ->
		          Nil
		        Cons x xs1 ->
		          Cons (f x) (map f xs1)
		    incAll xs =
		      map (\\x -> add x 1) xs
		    inverseAll xs =
		      map (\\b -> if b then False else True) xs
		    ints = Cons 1 (Cons 2 (Cons 3 Nil))
		    bools = Cons True (Cons False Nil)
		    incResult = incAll ints
		    inverseResult = inverseAll bools
		  in
		    Pair_ incResult inverseResult
		mapAndUseTwiceLeft =
		  case mapAndUseTwice of
		    Pair_ left a -> left
		mapAndUseTwiseRight =
		  case mapAndUseTwice of
		    Pair_ a right -> right
		""";
		var module = new ModuleTester(src, CompileLevel.BYTECODE_GEN);
		module.getType("mapAndUseTwice").is("Pair (List I32) (List Bool)");
		module.getValue("mapAndUseTwiceLeft").isWrittenIn("Cons 2 (Cons 3 (Cons 4 Nil))");
		module.getValue("mapAndUseTwiseRight").isWrittenIn("Cons False (Cons True Nil)");
	}

	@Test
	void genericTypeInLetExp() {
		String src ="""
		type IntList =
		  | Nil
		  | Cons I32 IntList
		car list =
		  case list of
		    Nil ->
		      0
		    Cons hd tl ->
		      hd
		rectest =
		  let
		    id x =
		      x
		    res =
		      Cons (id 1) (Cons (car (id (Cons 2 Nil))) Nil)
		  in
		    res
		""";

		var module = new ModuleTester(src, CompileLevel.BYTECODE_GEN);
		module.getValue("rectest").isWrittenIn("Cons 1 (Cons 2 Nil)");
	}
}

