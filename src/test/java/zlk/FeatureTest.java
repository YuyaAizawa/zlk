package zlk;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import zlk.ast.AnType;
import zlk.ast.Decl;
import zlk.common.RecordField;
import zlk.common.Type;
import zlk.common.id.Id;
import zlk.runtime.ZlkCustom;
import zlk.runtime.ZlkRecord;
import zlk.runtime.ZlkValue;
import zlk.idcalc.IcExp;
import zlk.idcalc.IcModule;
import zlk.idcalc.IcCtor;
import zlk.tester.DumpOnFailureWatcher;
import zlk.tester.ModuleTester;
import zlk.tester.ModuleTester.CompileLevel;
import zlk.tester.ValueTester.VData;
import zlk.util.collection.Seq;

@ExtendWith(DumpOnFailureWatcher.class)
public class FeatureTest {
	@Test
	void parsesTypeAliasWithOpenRecordBody() {
		var module = new ModuleTester(
				"type alias Foo a = { a | y : Bool, x : I32 }",
				CompileLevel.PARSE);

		Decl.TypeAlias alias = assertInstanceOf(Decl.TypeAlias.class, module.getAst().decls().head());
		assertEquals("Foo", alias.name());
		assertEquals(Seq.of("a"), alias.vars().map(AnType.Var::name));
		AnType.Record body = assertInstanceOf(AnType.Record.class, alias.body());
		assertEquals("a", body.extension().orElseThrow().name());
		assertEquals(Seq.of("y", "x"), body.fields().map(AnType.RecordField::name));
		assertEquals("type alias Foo a = { a | y : Bool, x : I32 }", alias.buildString());
	}

	@Test
	void parsesClosedAndOpenRecordsAsTypeConstructorArguments() {
		var module = new ModuleTester("""
				type alias Foo a = { a | x : I32 }
				closed : Foo {}
				closed = { x = 1 }
				wider : Foo { z : I32 }
				wider = { x = 1, z = 2 }
				""", CompileLevel.PARSE);

		Decl.ValDecl closed = assertInstanceOf(Decl.ValDecl.class, module.getAst().decls().at(1));
		Decl.ValDecl wider = assertInstanceOf(Decl.ValDecl.class, module.getAst().decls().at(2));
		AnType.Type closedAnno = assertInstanceOf(AnType.Type.class, closed.anno().orElseThrow());
		AnType.Type widerAnno = assertInstanceOf(AnType.Type.class, wider.anno().orElseThrow());
		assertInstanceOf(AnType.Record.class, closedAnno.args().head());
		assertInstanceOf(AnType.Record.class, widerAnno.args().head());
		assertTrue(module.getParseErrors().isEmpty());
	}

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

		Type.Var flag = new Type.Var("a");
		Type.Var value = new Type.Var("b");
		Type.Var tag = new Type.Var("d");
		Type.RowVar innerTail = new Type.RowVar("c");
		Type.RowVar outerTail = new Type.RowVar("e");
		module.getType("extract").is(new Type.Arrow(
				new Type.Record(
						Seq.of(
								new RecordField<>("outer", new Type.Record(
										Seq.of(
												new RecordField<>("flag", flag),
												new RecordField<>("value", value)),
										Optional.of(innerTail))),
								new RecordField<>("tag", tag)),
						Optional.of(outerTail)),
				value));
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

	// ===== 透明型エイリアス探索テスト群 =====

	@Test
	void aliasExpandsEmptyRecordArgumentToClosedRow() {
		// Foo a = { a | x : I32, y : Bool }
		// Foo {} -> { x : I32, y : Bool } (tail close)
		String src = """
				type alias Foo a = { a | x : I32, y : Bool }
				closed : Foo {}
				closed = { x = 1, y = True }
				""";
		var module = new ModuleTester(src, CompileLevel.TYPE_RECON);

		Type.Record expected = new Type.Record(Seq.of(
				new RecordField<>("x", Type.I32),
				new RecordField<>("y", Type.BOOL)));
		module.getType("closed").is(expected);
	}

	@Test
	void aliasExpandsWiderClosedRecordArgument() {
		// Foo {} -> {x,y}; Foo { z : I32 } -> {x,y,z}
		String src = """
				type alias Foo a = { a | x : I32, y : Bool }
				wider : Foo { z : I32 }
				wider = { x = 1, y = True, z = 2 }
				""";
		var module = new ModuleTester(src, CompileLevel.TYPE_RECON);

		Type.Record expected = new Type.Record(Seq.of(
				new RecordField<>("x", Type.I32),
				new RecordField<>("y", Type.BOOL),
				new RecordField<>("z", Type.I32)));
		module.getType("wider").is(expected);
	}

	@Test
	void plainTypeAliasExpandsInValueAnnotation() {
		// type alias Box a = { value : a } (TYPE alias)
		String src = """
				type alias Box a = { value : a }
				box : Box I32
				box = { value = 1 }
				""";
		var module = new ModuleTester(src, CompileLevel.TYPE_RECON);

		Type.Record expected = new Type.Record(Seq.of(
				new RecordField<>("value", Type.I32)));
		module.getType("box").is(expected);
	}

	@Test
	void aliasAllowsForwardReference() {
		String src = """
				type alias B = A I32
				type A a = A0 a
				zero : B
				zero = A0 0
				""";
		var module = new ModuleTester(src, CompileLevel.TYPE_RECON);
		module.getType("zero").is(new Type.CtorApp(Id.intern("Main.A"), Seq.of(Type.I32)));
	}

	@Test
	void aliasNotPresentInIcModuleTypes() {
		String src = """
				type alias Box a = { value : a }
				box : Box I32
				box = { value = 1 }
				type Real = Real I32
				""";
		var module = new ModuleTester(src, CompileLevel.NAME_EVAL);
		IcModule ic = module.getIdcalcModule();
		assertTrue(ic.types().anyMatch(d -> d.id().simpleName().equals("Real")));
		assertFalse(ic.types().anyMatch(d -> d.id().simpleName().equals("Box")));
	}

	@Test
	void selfRecursiveAliasIsRejected() {
		assertThrows(RuntimeException.class, () -> new ModuleTester("""
				type alias Bad a = Bad a
				t : Bad I32
				t = 0
				""", CompileLevel.NAME_EVAL));
	}

	@Test
	void mutuallyRecursiveAliasIsRejected() {
		assertThrows(RuntimeException.class, () -> new ModuleTester("""
				type alias A a = B a
				type alias B a = A a
				t : A I32
				t = 0
				""", CompileLevel.NAME_EVAL));
	}

	@Test
	void aliasArityMismatchIsRejected() {
		// Boxはarity 1だが0引数適用
		assertThrows(RuntimeException.class, () -> new ModuleTester("""
				type alias Box a = { value : a }
				bad : Box
				bad = { value = 1 }
				""", CompileLevel.NAME_EVAL));
	}

	@Test
	void aliasPreservesOpenRowArgument() {
		var module = new ModuleTester("""
				type alias Foo a = { a | x : I32 }
				keep : Foo a -> Foo a
				keep value = value
				""", CompileLevel.NAME_EVAL);
		Type.Record open = new Type.Record(
				Seq.of(new RecordField<>("x", Type.I32)),
				java.util.Optional.of(new Type.RowVar("a")));
		Type annotation = module.getIdcalcModule().decls().head().anno().orElseThrow();
		assertEquals(new Type.Arrow(open, open), annotation);
	}

	@Test
	void aliasAllowsForwardReferenceToAnotherAlias() {
		var module = new ModuleTester("""
				type alias B a = A a
				type alias A a = { value : a }
				box : B I32
				box = { value = 1 }
				""", CompileLevel.TYPE_RECON);
		module.getType("box").is(new Type.Record(
				Seq.of(new RecordField<>("value", Type.I32))));
	}

	@Test
	void adtConstructorArgumentCanUseAlias() {
		var module = new ModuleTester("""
				type alias Box a = { value : a }
				type Wrapped a = Wrapped (Box a)
				""", CompileLevel.NAME_EVAL);
		Type ctorArg = module.getIdcalcModule().types().head().ctors().head().args().head();
		assertEquals(new Type.Record(
				Seq.of(new RecordField<>("value", new Type.Var("a")))), ctorArg);
	}

	@Test
	void unknownTypeNameIsRejected() {
		assertThrows(RuntimeException.class, () -> new ModuleTester("""
				bad : Missing
				bad = 0
				""", CompileLevel.NAME_EVAL));
	}

	@Test
	void rowAliasRejectsValueTypeArgument() {
		assertThrows(RuntimeException.class, () -> new ModuleTester("""
				type alias Foo a = { a | x : I32 }
				bad : Foo I32
				bad = { x = 0 }
				""", CompileLevel.NAME_EVAL));
	}

	@Test
	void aliasParameterCannotHaveBothKinds() {
		assertThrows(RuntimeException.class, () -> new ModuleTester("""
				type alias Bad a = { a | value : a }
				""", CompileLevel.NAME_EVAL));
	}

	@Test
	void annotationVariableCannotHaveBothKinds() {
		assertThrows(RuntimeException.class, () -> new ModuleTester("""
				bad : { a | value : a }
				bad = { value = 0 }
				""", CompileLevel.NAME_EVAL));
	}

	@Test
	void aliasExpansionRejectsDuplicateLabel() {
		assertThrows(RuntimeException.class, () -> new ModuleTester("""
				type alias Foo a = { a | x : I32 }
				bad : Foo { x : Bool }
				bad = { x = 0 }
				""", CompileLevel.NAME_EVAL));
	}

	@Test
	void adtParameterCanBeUsedAsRowParameter() {
		var module = new ModuleTester("""
				type Foo a = Foo { a | bar : I32 }
				""", CompileLevel.NAME_EVAL);

		var foo = module.getIdcalcModule().types().head();
		Type rowParameter = new Type.Record(
				Seq.of(), Optional.of(new Type.RowVar("a")));
		assertEquals(Seq.of(rowParameter), foo.vars());

		Type ctorArg = foo.ctors().head().args().head();
		assertEquals(new Type.Record(
				Seq.of(new RecordField<>("bar", Type.I32)),
				Optional.of(new Type.RowVar("a"))), ctorArg);
	}

	@Test
	void adtRowParameterKindPropagatesAcrossMutuallyRecursiveNominalReferences() {
		var module = new ModuleTester("""
				type A r = A (B r)
				type B r = B (A r) | BEnd { r | value : I32 }
				""", CompileLevel.NAME_EVAL);

		Type rowParameter = new Type.Record(
				Seq.of(), Optional.of(new Type.RowVar("r")));
		module.getIdcalcModule().types().forEach(type ->
				assertEquals(Seq.of(rowParameter), type.vars()));
	}

	@Test
	void adtRowParameterKindPropagatesThroughAlias() {
		var module = new ModuleTester("""
				type alias Open r = { r | value : I32 }
				type Box r = Box (Open r)
				""", CompileLevel.NAME_EVAL);

		Type rowParameter = new Type.Record(
				Seq.of(), Optional.of(new Type.RowVar("r")));
		assertEquals(Seq.of(rowParameter), module.getIdcalcModule().types().head().vars());
	}

	@Test
	void adtParameterCannotHaveDifferentKindsAcrossConstructors() {
		assertThrows(RuntimeException.class, () -> new ModuleTester("""
				type Bad a = AsType a | AsRow { a | value : I32 }
				""", CompileLevel.NAME_EVAL));
	}

	@Test
	void adtRowParameterRejectsValueTypeArgument() {
		assertThrows(RuntimeException.class, () -> new ModuleTester("""
				type Foo r = Foo { r | value : I32 }
				bad : Foo I32
				bad = Foo { value = 0 }
				""", CompileLevel.NAME_EVAL));
	}

	@Test
	void duplicateAliasParameterIsRejected() {
		// duplicate alias parameterをMap初期化で黙って上書きせず検出する．
		assertThrows(RuntimeException.class, () -> new ModuleTester("""
				type alias Bad a a = { x : a }
				""", CompileLevel.NAME_EVAL));
	}

	@Test
	void adtConstructorRejectsUndeclaredTypeParameter() {
		// type Bad a = Bad b — bはADT parameterとして宣言されていないため，
		// ADT constructor引数文脈では未宣言変数を暗黙導入せず拒否する．
		assertThrows(RuntimeException.class, () -> new ModuleTester("""
				type Bad a = Bad b
				""", CompileLevel.NAME_EVAL));
	}

	@Test
	void adtConstructorRejectsDuplicateTypeParameter() {
		// type Bad a a = Bad a — ADT parameterの重複宣言を拒否する．
		assertThrows(RuntimeException.class, () -> new ModuleTester("""
				type Bad a a = Bad a
				""", CompileLevel.NAME_EVAL));
	}

	@Test
	void aliasBodyRejectsUndeclaredTypeParameter() {
		// type alias Bad a = b — alias body文脈では未宣言変数 b を拒否する．
		assertThrows(RuntimeException.class, () -> new ModuleTester("""
				type alias Bad a = b
				""", CompileLevel.NAME_EVAL));
	}

	@Test
	void valueAnnotationImplicitlyIntroducesTypeParameter() {
		// value annotation文脈では未宣言変数を暗黙導入する．
		// id : a -> a の a はTYPEとして導入される．
		var module = new ModuleTester("""
				id : a -> a
				id x = x
				""", CompileLevel.NAME_EVAL);
		Type annoId = module.getIdcalcModule().decls().head().anno().orElseThrow();
		assertEquals(new Type.Arrow(new Type.Var("a"), new Type.Var("a")), annoId);
	}

	// ===== alias/backend end-to-endテスト群 =====

	@Test
	void rowParameterizedAdtRunsAtBytecodeLevel() {
		var module = new ModuleTester("""
				type Foo r = Foo { r | bar : I32 }
				makeFoo = Foo
				foo = makeFoo { bar = 1, baz = True }
				other = Foo { bar = 2, qux = 3 }
				getBaz wrapped =
				  case wrapped of
				    Foo record -> record.baz
				getQux wrapped =
				  case wrapped of
				    Foo record -> record.qux
				boolResult = getBaz foo
				intResult = getQux other
				""", CompileLevel.BYTECODE_GEN);

		assertEquals(true, ((VData) module.getValue("boolResult")).value());
		assertEquals(3, ((VData) module.getValue("intResult")).value());
	}

	@Test
	void aliasEndToEndResolvesToClosedRecordAndGeneratesNoAliasClass() throws ReflectiveOperationException {
		String src = """
				type alias Foo a = { a | x : I32, y : Bool }
				bar : Foo { z : I32 } -> I32
				bar record = add record.x record.z
				result = bar { x = 1, y = True, z = 2 }
				""";

		var module = new ModuleTester(src, CompileLevel.BYTECODE_GEN);

		// bar resolved typeはclosed { x:I32, y:Bool, z:I32 } -> I32
		Type.Record expectedArg = new Type.Record(Seq.of(
				new RecordField<>("x", Type.I32),
				new RecordField<>("y", Type.BOOL),
				new RecordField<>("z", Type.I32)));
		module.getType("bar").is(new Type.Arrow(expectedArg, Type.I32));

		// result実行値3
		assertEquals(3, ((VData) module.getValue("result")).value());

		// alias用 Main$Foo classは生成されない
		assertFalse(module.getGeneratedClassNames().contains("Main$Foo"));

		// bar reflection parameter typeは zlk.runtime.ZlkRecord
		java.lang.reflect.Method barMethod = module.getMainMethod("bar");
		Class<?>[] paramTypes = barMethod.getParameterTypes();
		assertEquals(1, paramTypes.length);
		assertEquals(zlk.runtime.ZlkRecord.class, paramTypes[0]);
	}

	@Test
	void inferredAccessorAcceptsWiderShapesAtBytecodeLevel() {
		String src = """
				getX record = record.x
				intResult = getX { x = 1, y = True }
				nestedResult = getX { x = 2, z = 3, w = False }
				""";

		var module = new ModuleTester(src, CompileLevel.BYTECODE_GEN);

		// inferred accessorが異なるwider shapesでBYTECODE_GEN実行し値をassert
		assertEquals(1, ((VData) module.getValue("intResult")).value());
		assertEquals(2, ((VData) module.getValue("nestedResult")).value());
	}

	@Test
	void polymorphicUpdatePreservesExtraFieldsInZlkRecord() {
		String src = """
				setX record value = { record | x = value }
				base = { y = True, x = 1 }
				updated = setX base 2
				updatedX = updated.x
				updatedY = updated.y
				baseX = base.x
				""";

		var module = new ModuleTester(src, CompileLevel.BYTECODE_GEN);

		ZlkRecord updatedValue = (ZlkRecord) ((VData) module.getValue("updated")).value();
		assertEquals(2, updatedValue.get("x"));
		assertEquals(true, updatedValue.get("y"));
		assertEquals("{ x = 2, y = True }", updatedValue.toString());
		assertEquals(2, ((VData) module.getValue("updatedX")).value());
		assertEquals(true, ((VData) module.getValue("updatedY")).value());
		assertEquals(1, ((VData) module.getValue("baseX")).value());
	}

	@Test
	void annotatedRowPolymorphicAccessorRunsAtBytecodeLevel() {
		String src = """
				getX : { r | x : I32 } -> I32
				getX record = record.x
				intResult = getX { x = 1, y = True }
				nestedResult = getX { x = 42, z = 3, w = False }
				""";

		var module = new ModuleTester(src, CompileLevel.BYTECODE_GEN);

		assertEquals(1, ((VData) module.getValue("intResult")).value());
		assertEquals(42, ((VData) module.getValue("nestedResult")).value());
	}

	@Test
	void letInThenBranchDoesNotLeakToElseBranch() {
		// then 側の let で宣言した名前が else 側から見えてはならない．
		// 退出後に binding を破棄する一時 frame の検証．
		assertThrows(RuntimeException.class, () -> new ModuleTester("""
				f n =
				  if isZero n then
				    let
				      one = 1
				    in
				      one
				  else
				    one
				""", CompileLevel.NAME_EVAL));
	}

	@Test
	void ctorSignatureAndIcCtorArgsShareResolvedArguments() {
		// 改善G characterization: ADT constructor引数型は一度だけ解決され，
		// value constructor signature (IcVarCtor.type) と IcCtor.args が
		// 同じ解決結果から供給される．aliasをconstructor引数に持つADTで，
		// IcCtor.args と IcVarCtor.type().flatten() の引数部分が同一の
		// semantic Type であることを確認する．
		var module = new ModuleTester("""
				type alias Box a = { value : a }
				type Wrapped a = Wrapped (Box a)
				wrapped = Wrapped { value = 1 }
				""", CompileLevel.NAME_EVAL);

		IcModule ic = module.getIdcalcModule();
		IcCtor ctor = ic.types().head().ctors().head();
		// IcCtor.args: [Box a展開後のRecord]
		Seq<Type> ctorArgs = ctor.args();

		// value宣言 wrapped の body は IcApp(IcVarCtor(Wrapped), [record])．
		// IcVarCtor.type は constructor signature = fromSeq(args ++ [retTy])．
		IcExp body = ic.decls().head().body();
		IcExp.IcApp app = assertInstanceOf(IcExp.IcApp.class, body);
		IcExp.IcVarCtor varCtor = assertInstanceOf(IcExp.IcVarCtor.class, app.fun());
		// flatten() は [arg1, ..., retTy]．dropLast で引数部分を得る．
		Seq<Type> sigArgs = varCtor.type().flatten().dropLast();

		assertEquals(ctorArgs, sigArgs);
	}
}
