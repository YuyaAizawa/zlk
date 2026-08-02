package zlk.feature.customtype;

import org.junit.jupiter.api.Test;

import zlk.fixture.CompilationFixture;

public class CustomTypeTypingFeatureTest {
	@Test
	void genericDatatypesAreInstantiatedWithEachElementType() {
		var module = CompilationFixture.compileSucceeded(
				"""
				type List a =
				  | Nil
				  | Cons a (List a)

				type Pair a b = Pair_ a b

				intList = Cons 1 Nil
				boolList = Cons True Nil
				pair = Pair_ 1 True
				""");

		module.assertType("intList", "Main.List I32");
		module.assertType("boolList", "Main.List Bool");
		module.assertType("pair", "Main.Pair I32 Bool");
	}
}
