package zlk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import zlk.tester.DumpOnFailureWatcher;
import zlk.tester.ModuleTester;
import zlk.tester.ModuleTester.CompileLevel;

@ExtendWith(DumpOnFailureWatcher.class)
public class FeatureTest {
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
	void enumDeclAndCaseExp() {
		String src="""
		type IntList = Nil | Cons I32 IntList

		sum list =
		  case list of
		    | Nil -> 0
		    | Cons hd tl -> add hd (sum tl)

		ans = sum (Cons 3 (Cons 2 (Cons 1 Nil)))
		""";
		var module = new ModuleTester(src, CompileLevel.BYTECODE_GEN);
		var ans = module.getValue("ans");
		ans.is(6);
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
		// module.getValue("intBoolPair").isWrittenIn("Pair I32 Bool"); TODO: 作る
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
		| Pair_ a b -> add a b
		""";
		var module = new ModuleTester(src, CompileLevel.BYTECODE_GEN);
		module.getType("f1").is("I32 -> I32");
		module.getType("f2").is("Pair I32 a");
		module.getValue("a1").is(3);
		module.getValue("a2").is(3);
	}

	// TODO ラムダ式の対応後に追加
//	@Test
//	void mapAndUseTwice() {
//		String src="""
//		type List a = Nil | Cons a (List a)
//		type Pair a b = Pair_ a b
//		mapAndUseTwice =
//		  let
//		    map f xs =
//		      case xs of
//		      | Nil ->
//		          Nil
//		      | Cons x xs1 ->
//		          Cons (f x) (map f xs1)
//		   incAll xs =
//		      map (\\x -> x + 1) xs
//		    negateAll xs =
//		      map (\\b -> if b then False else True) xs
//		    ints = Cons 1 (Cons 2 (Cons 3 Nil))
//		    bools = Cons True (Cons False Nil)
//		    incResult    = incAll ints
//		    negateResult = negateAll bools
//		  in
//		    Pair_ incResult negateResult
//		mapAndUseTwiceLeft =
//		  case mapAndUseTwice of
//		  | Pair_ left _ -> left
//		mapAndUseTwiseRight =
//		  case mapAndUseTwice of
//		  | Pair_ _ right -> right
//		""";
//		var module = new ModuleTester(src, CompileLevel.BYTECODE_GEN);
//		module.getType("mapAndUseTwice").is("Pair (List I32) (List Bool)");
//		// module.getValue("mapAndUseTwiceLeft").isWrittenIn("Cons 2 (Cons 3 (Cons 4 Nil))"); TODO: 作る
//	}

	@Test
	void genericTypeInLetExp() {
		String src ="""
		type IntList =
		| Nil
		| Cons I32 IntList
		car list =
		  case list of
		  | Nil ->
		    0
		  | Cons hd tl ->
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
		// module.getValue("rectest").stringExpIs("Cons 1 (Cons 2 Nil)");  // TODO: ユーザー定義型のtoString
	}
}

