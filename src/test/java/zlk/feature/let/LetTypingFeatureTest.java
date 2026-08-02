package zlk.feature.let;

import org.junit.jupiter.api.Test;

import zlk.fixture.CompilationFixture;

public class LetTypingFeatureTest {
	@Test
	void localPolymorphicFunctionIsInstantiatedAtEachUse() {
		var module = CompilationFixture.compileSucceeded(
				"""
				type List a =
				  | Nil
				  | Cons a (List a)

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
				""");

		module.assertType("rectest.id", "a -> a");
		module.assertType("rectest.res", "Main.List I32");
	}

	@Test
	void localBindingRetainsCapturedConcreteType() {
		var module = CompilationFixture.compileSucceeded(
				"""
				c = 1
				fun =
				  let
				    f = c
				  in
				    f
				""");

		module.assertType("fun", "I32");
	}

	@Test
	void localBindingSharesOuterParameterType() {
		var module = CompilationFixture.compileSucceeded(
				"""
				idLet x =
				  let
				    y = x
				  in
				    y
				""");

		module.assertType("idLet", "a -> a");
	}
}
