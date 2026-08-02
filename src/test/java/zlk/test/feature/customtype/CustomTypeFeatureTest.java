package zlk.test.feature.customtype;

import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import zlk.runtime.ZlkCustom;
import zlk.util.tester.DumpOnFailureWatcher;
import zlk.util.tester.ModuleTester;
import zlk.util.tester.ModuleTester.CompileLevel;
import zlk.util.tester.ValueTester.VData;

@ExtendWith(DumpOnFailureWatcher.class)
public class CustomTypeFeatureTest {
	@Test
	void adtIsSealedInterfaceAndRecords() throws ReflectiveOperationException {
		String src =
				"""
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
	void adtToStringPreservesZlkValues() {
		String src =
				"""
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
		String src =
				"""
				type A = A B | A0
				type B = B A | B0

				x = A (B (A B0))
				""";

		var module = new ModuleTester(src, CompileLevel.BYTECODE_GEN);
		Object x = ((VData) module.getValue("x")).value();
		assertEquals("A (B (A B0))", x.toString());
	}

	@Test
	void enumDeclAndCaseExp() {
		String src=
				"""
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
	void pairType() {
		String src=
				"""
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
	void rowParameterizedAdtRunsAtBytecodeLevel() {
		var module = new ModuleTester(
				"""
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
}
