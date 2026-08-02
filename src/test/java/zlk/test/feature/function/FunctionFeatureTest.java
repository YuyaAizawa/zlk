package zlk.test.feature.function;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import zlk.util.fixture.CompilationFixture;
import zlk.util.tester.DumpOnFailureWatcher;

@ExtendWith(DumpOnFailureWatcher.class)
public class FunctionFeatureTest {
	@Test
	void selfRecursiveFunction() {
		String src =
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
				""";

		var module = CompilationFixture.compileSucceeded(src);
		int zeroFactorial = (int) module.value("fact", 0);
		int fiveFactorial = (int) module.value("fact", 5);
		assertEquals(1, zeroFactorial);
		assertEquals(120, fiveFactorial);
	}

	@Test
	void closuerConversion() {
		String src =
				"""
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
		var module = CompilationFixture.compileSucceeded(src);
		int actual = (int) module.value("make_adder", 1, 2, 3);
		assertEquals(6, actual);
	}

	@Test
	void selfRecursiveAndClosure() {
		String src =
				"""
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
		var module = CompilationFixture.compileSucceeded(src);
		int actual = (int) module.value("ans");
		assertEquals(16, actual);
	}

	@Test
	void mutualRecursionAndClosure() {
		String src=
				"""
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
		var module = CompilationFixture.compileSucceeded(src);
		module.assertType("makeEvenFromOffset", "I32 -> I32 -> Bool");
		boolean actual = (boolean) module.value("main");
		assertEquals(true, actual);
	}

	@Test
	void genericFunction() {
		String src=
				"""
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
		var module = CompilationFixture.compileSucceeded(src);
		module.assertType("map", "(a -> b) -> Main.List a -> Main.List b");
		Object actual = module.value("test");
		assertEquals("Cons True (Cons False (Cons False Nil))", actual.toString());
	}

	@Test
	void genericAndClosure() {
		String src=
				"""
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
		var module = CompilationFixture.compileSucceeded(src);
		module.assertType("test", "Main.Pair I32 Bool");
		Object actual = module.value("test");
		assertEquals("Pair_ 1 True", actual.toString());
	}

	@Test
	void leftPartialApplication() {
		String src=
				"""
				fun x y = add x y
				f1 = fun 1

				type Pair a b = Pair_ a b
				f2 = Pair_ 1

				a1 = f1 2
				a2 = case f2 2 of
				  Pair_ a b -> add a b
				""";
		var module = CompilationFixture.compileSucceeded(src);
		module.assertType("f1", "I32 -> I32");
		module.assertType("f2", "a -> Main.Pair I32 a");
		int a1 = (int) module.value("a1");
		int a2 = (int) module.value("a2");
		assertEquals(3, a1);
		assertEquals(3, a2);
	}

	@Test
	void minimumLambda() {
		String src =
				"""
				id =
				  \\x -> x
				apply =
				  \\f x -> f x
				add_ =
				  \\x y -> add x y
				ans =
				  (\\x -> add x 1) 2
				""";
		var module = CompilationFixture.compileSucceeded(src);
		module.assertType("id", "a -> a");
		module.assertType("apply", "(a -> b) -> a -> b");
		module.assertType("add_", "I32 -> I32 -> I32");
		int actual = (int) module.value("ans");
		assertEquals(3, actual);
	}

	@Test
	void mapAndUseTwice() {
		String src=
				"""
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
		var module = CompilationFixture.compileSucceeded(src);
		module.assertType("mapAndUseTwice", "Main.Pair (Main.List I32) (Main.List Bool)");
		Object left = module.value("mapAndUseTwiceLeft");
		Object right = module.value("mapAndUseTwiseRight");
		assertEquals("Cons 2 (Cons 3 (Cons 4 Nil))", left.toString());
		assertEquals("Cons False (Cons True Nil)", right.toString());
	}

	@Test
	void genericTypeInLetExp() {
		String src =
				"""
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

		var module = CompilationFixture.compileSucceeded(src);
		Object actual = module.value("rectest");
		assertEquals("Cons 1 (Cons 2 Nil)", actual.toString());
	}

	// ===== 透明型エイリアス探索テスト群 =====
}
