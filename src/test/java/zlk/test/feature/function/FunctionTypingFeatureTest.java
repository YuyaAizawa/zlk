package zlk.test.feature.function;

import org.junit.jupiter.api.Test;

import zlk.util.fixture.CompilationFixture;

public class FunctionTypingFeatureTest {
	@Test
	void selfRecursiveFunction() {
		var module = CompilationFixture.compileSucceeded(
				"""
				fact n =
				  if isZero n then
				    1
				  else
				    let
				      one = 1
				      nn = sub n one
				    in
				      mul n (fact nn)
				""");

		module.assertType("fact", "I32 -> I32");
	}

	@Test
	void mutualRecursiveFunction() {
		var module = CompilationFixture.compileSucceeded(
				"""
				isEven n =
				  if isZero n then
				    True
				  else
				    isOdd (sub n 1)

				isOdd n =
				  if isZero n then
				    False
				  else
				    isEven (sub n 1)
				""");

		module.assertType("isEven", "I32 -> Bool");
		module.assertType("isOdd", "I32 -> Bool");
	}

	@Test
	void polymorphicFunctionsRemainIndependentInsideEncodedPair() {
		var module = CompilationFixture.compileSucceeded(
				"""
				pair a b s = s a b
				fst p = p fst_
				fst_ x y = x
				snd p = p snd_
				snd_ x y = y

				id x = x

				p = pair id id

				u = fst p
				v = snd p

				r1 = u 1
				r2 = v True
				""");

		module.assertType("p", "((a -> a) -> (b -> b) -> c) -> c");
		module.assertType("r1", "I32");
		module.assertType("r2", "Bool");
	}
}
