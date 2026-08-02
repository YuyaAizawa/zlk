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
import zlk.util.fixture.CompilationFixture;
import zlk.util.tester.DumpOnFailureWatcher;

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

		var module = CompilationFixture.compileSucceeded(src);
		Object none = module.value("none");
		Object sameNone = module.value("sameNone");
		Object some = module.value("some");
		Object sameSome = module.value("sameSome");
		Object otherSome = module.value("otherSome");

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

		var module = CompilationFixture.compileSucceeded(src);
		Object pair = module.value("pair");
		Object list = module.value("list");
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

		var module = CompilationFixture.compileSucceeded(src);
		Object x = module.value("x");
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
		var module = CompilationFixture.compileSucceeded(src);
		int actual = (int) module.value("ans");
		assertEquals(6, actual);
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
		var module = CompilationFixture.compileSucceeded(src);
		module.assertType("left", "Main.Pair a b -> a");
		module.assertType("right", "Main.Pair a b -> b");
		module.assertType("oneTrueLeft", "I32");
		module.assertType("oneTrueRight", "Bool");
		int left = (int) module.value("oneTrueLeft");
		boolean right = (boolean) module.value("oneTrueRight");
		assertEquals(1, left);
		assertEquals(true, right);
	}

	@Test
	void rowParameterizedAdtRunsAtBytecodeLevel() {
		var module = CompilationFixture.compileSucceeded(
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
				""");

		boolean boolResult = (boolean) module.value("boolResult");
		int intResult = (int) module.value("intResult");
		assertEquals(true, boolResult);
		assertEquals(3, intResult);
	}
}
