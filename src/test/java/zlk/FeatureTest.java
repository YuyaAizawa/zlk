package zlk;

import org.junit.jupiter.api.Test;

import zlk.tester.ModuleTester;

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

		var module = new ModuleTester(src);
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
		var module = new ModuleTester(src);
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
		var module = new ModuleTester(src);
		var ans = module.getValue("ans");
		ans.is(6);
	}
}

