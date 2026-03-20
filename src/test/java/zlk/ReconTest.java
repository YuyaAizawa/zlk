package zlk;

import org.junit.jupiter.api.Test;

import zlk.idcalc.IcCaseBranch;
import zlk.idcalc.IcExp.IcApp;
import zlk.idcalc.IcExp.IcCase;
import zlk.idcalc.IcExp.IcCnst;
import zlk.idcalc.IcExp.IcVarLocal;
import zlk.idcalc.IcPattern;
import zlk.tester.ModuleTester;
import zlk.tester.ModuleTester.CompileLevel;

public class ReconTest {
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

		var module = new ModuleTester(src, CompileLevel.TYPE_RECON);
		module.getType("fact").is("I32 -> I32");
	}

	@Test
	void mutualRecursiveFunction() {
		String src ="""
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
		""";

		var module = new ModuleTester(src, CompileLevel.TYPE_RECON);
		module.getType("isEven").is("I32 -> Bool");
		module.getType("isOdd").is("I32 -> Bool");
	}

	@Test
	void genericTypeInLetExp() {
		String src ="""
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
				""";

		var module = new ModuleTester(src, CompileLevel.TYPE_RECON);
		module.getType("rectest.id").is("a -> a");
		module.getType("rectest.res").is("List I32");
	}

	@Test
	void leakFlex() {
		String src ="""
				c = 1
				fun =
				  let
				    f = c
				  in
				    f
				""";
		var module = new ModuleTester(src, CompileLevel.TYPE_RECON);
		module.getType("fun").is("I32");
	}

	@Test
	void leakOuterStruct() {
		String src ="""
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
				""";
		var module = new ModuleTester(src, CompileLevel.TYPE_RECON);
		module.getType("p").is("((a -> a) -> (b -> b) -> c) -> c");
		module.getType("r1").is("I32");
		module.getType("r2").is("Bool");
	}

	@Test
	void userDefinedGenericDatatype() {
		String src ="""
				type List a =
				  | Nil
				  | Cons a (List a)

				type Pair a b = Pair_ a b

				intList = Cons 1 Nil
				boolList = Cons True Nil
				pair = Pair_ 1 True
				""";
		var module = new ModuleTester(src, CompileLevel.TYPE_RECON);
		module.getType("intList").is("List I32");
		module.getType("boolList").is("List Bool");
		module.getType("pair").is("Pair I32 Bool");
	}

	@Test
	void unifiedInLet() {
		String src =
				"""
				idLet x =
				  let
				    y = x
				  in
				    y
				""";

		var module = new ModuleTester(src, CompileLevel.TYPE_RECON);
		module.getType("idLet").is("a -> a");
	}

	void capturesExpressionAndPatternTypes() {
		String src ="""
				type List a =
				  | Nil
				  | Cons a (List a)

				head list =
				  case list of
				    Nil ->
				      0
				    Cons hd tl ->
				      add hd 1
				""";

		var module = new ModuleTester(src, CompileLevel.TYPE_RECON);

		var head = module.getIdcalcModule().decls().getFirst();
		IcPattern.Var listPat = (IcPattern.Var) head.args().getFirst();
		IcCase body = (IcCase) head.body();
		IcVarLocal target = (IcVarLocal) body.target();
		IcCaseBranch nilBranch = body.branches().get(0);
		IcCnst zero = (IcCnst) nilBranch.body();
		IcCaseBranch consBranch = body.branches().get(1);
		IcPattern.Dector consPat = (IcPattern.Dector) consBranch.pattern();
		IcPattern.Var hdPat = (IcPattern.Var) consPat.args().get(0).pattern();
		IcPattern.Var tlPat = (IcPattern.Var) consPat.args().get(1).pattern();
		IcApp addCall = (IcApp) consBranch.body();
		IcVarLocal hdRef = (IcVarLocal) addCall.args().get(0);
		IcCnst one = (IcCnst) addCall.args().get(1);

		module.getCallSiteType(addCall).is("I32");
		module.getCallSiteType(hdRef).is("I32");
		module.getCallSiteType(one).is("I32");
		module.getCallSiteType(zero).is("I32");
		module.getCallSiteType(target).is("List I32");

		module.getCallSiteType(listPat).is("List I32");
		module.getCallSiteType(consPat).is("List I32");
		module.getCallSiteType(hdPat).is("I32");
		module.getCallSiteType(tlPat).is("List I32");
	}
}
