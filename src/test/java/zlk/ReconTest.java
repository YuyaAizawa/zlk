package zlk;

import static org.junit.jupiter.api.Assertions.assertThrows;

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
	void fieldAccessInfersClosedSingleFieldRecord() {
		var module = new ModuleTester(
				"getX record = record.x",
				CompileLevel.TYPE_RECON);

		module.getType("getX").is("{ x : a } -> a");
	}

	@Test
	void completeRecordAnnotationAllowsAccessToMultipleFields() {
		String src = """
				sum : { x : I32, y : I32 } -> I32
				sum record = add record.x record.y
				""";
		var module = new ModuleTester(src, CompileLevel.TYPE_RECON);

		module.getType("sum").is("{ x : I32, y : I32 } -> I32");
	}

	@Test
	void inferredFieldAccessorDoesNotAcceptWiderRecordWithoutRowPolymorphism() {
		String src = """
				getX record = record.x
				result = getX { x = 1, y = True }
				""";

		assertThrows(RuntimeException.class,
				() -> new ModuleTester(src, CompileLevel.TYPE_RECON));
	}

	@Test
	void typeAnnotationSpecializesInferredType() {
		String src = """
				id : I32 -> I32
				id x = x
				""";

		var module = new ModuleTester(src, CompileLevel.TYPE_RECON);
		module.getType("id").is("I32 -> I32");
	}

	@Test
	void polymorphicTypeAnnotationCanSpecializeInferredVariables() {
		String src = """
				const : a -> a -> a
				const x y = x
				""";

		var module = new ModuleTester(src, CompileLevel.TYPE_RECON);
		module.getType("const").is("a -> a -> a");
	}

	@Test
	void polymorphicTypeAnnotationIsInstantiatedAtEachUse() {
		String src = """
				id : a -> a
				id x = x
				int = id 1
				bool = id True
				""";

		var module = new ModuleTester(src, CompileLevel.TYPE_RECON);
		module.getType("id").is("a -> a");
		module.getType("int").is("I32");
		module.getType("bool").is("Bool");
	}

	@Test
	void typeAnnotationCannotBeMoreGeneralThanInferredType() {
		String src = """
				bad : a -> a
				bad x = 1
				""";

		assertThrows(RuntimeException.class,
				() -> new ModuleTester(src, CompileLevel.TYPE_RECON));
	}

	@Test
	void typeAnnotationDescribesTheWholeValueType() {
		String src = """
				makeAdder : I32 -> I32 -> I32
				makeAdder x = \\y -> add x y
				""";

		var module = new ModuleTester(src, CompileLevel.TYPE_RECON);
		module.getType("makeAdder").is("I32 -> I32 -> I32");
	}

	@Test
	void localTypeAnnotationIsEnabled() {
		String src = """
				use =
				  let
				    id : a -> a
				    id x = x
				    int = id 1
				    bool = id True
				  in
				    int
				""";

		var module = new ModuleTester(src, CompileLevel.TYPE_RECON);
		module.getType("use.id").is("a -> a");
		module.getType("use.int").is("I32");
		module.getType("use.bool").is("Bool");
	}

	@Test
	void typeAnnotationSupportsUserDefinedTypes() {
		String src = """
				type List a =
				  | Nil
				  | Cons a (List a)

				singleton : a -> List a
				singleton x = Cons x Nil
				intList = singleton 1
				boolList = singleton True
				""";

		var module = new ModuleTester(src, CompileLevel.TYPE_RECON);
		module.getType("singleton").is("a -> List a");
		module.getType("intList").is("List I32");
		module.getType("boolList").is("List Bool");
	}

	@Test
	void mutuallyRecursiveAnnotationsHaveIndependentTypeVariables() {
		String src = """
				f : a -> a
				f x = g x

				g : b -> b
				g x = f x
				""";

		var module = new ModuleTester(src, CompileLevel.TYPE_RECON);
		module.getType("f").is("a -> a");
		module.getType("g").is("b -> b");
	}

	@Test
	void incompatibleMutuallyRecursiveAnnotationsAreRejected() {
		String src = """
				f : a -> a
				f x = g x

				g : b -> I32
				g x = f x
				""";

		assertThrows(RuntimeException.class,
				() -> new ModuleTester(src, CompileLevel.TYPE_RECON));
	}

	@Test
	void typeAnnotationBreaksInferenceDependencyCycle() {
		String src = """
				f : a -> a
				f x = g x

				g x = f x
				""";

		var module = new ModuleTester(src, CompileLevel.TYPE_RECON);
		module.getType("f").is("a -> a");
		module.getType("g").is("a -> a");
	}

	@Test
	void recursiveTypeAnnotationIsRigidInItsOwnBody() {
		String src = """
				f : a -> a
				f x = f 1
				""";

		assertThrows(RuntimeException.class,
				() -> new ModuleTester(src, CompileLevel.TYPE_RECON));
	}

	@Test
	void nestedTypeAnnotationsShareOuterRigidVariable() {
		String src = """
				outer : a -> a -> a
				outer x =
				  let
				    inner : a -> a
				    inner y = x
				  in
				    inner
				""";

		var module = new ModuleTester(src, CompileLevel.TYPE_RECON);
		module.getType("outer").is("a -> a -> a");
		module.getType("outer.inner").is("a -> a");
	}

	@Test
	void localTypeAnnotationCannotCaptureOuterTypeVariable() {
		String src = """
				outer x =
				  let
				    f : a -> a
				    f y = x
				  in
				    f
				""";

		assertThrows(RuntimeException.class,
				() -> new ModuleTester(src, CompileLevel.TYPE_RECON));
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

		var head = module.getIdcalcModule().decls().head();
		IcPattern.Var listPat = (IcPattern.Var) head.args().head();
		IcCase body = (IcCase) head.body();
		IcVarLocal target = (IcVarLocal) body.target();
		IcCaseBranch nilBranch = body.branches().at(0);
		IcCnst zero = (IcCnst) nilBranch.body();
		IcCaseBranch consBranch = body.branches().at(1);
		IcPattern.Dector consPat = (IcPattern.Dector) consBranch.pattern();
		IcPattern.Var hdPat = (IcPattern.Var) consPat.args().at(0).pattern();
		IcPattern.Var tlPat = (IcPattern.Var) consPat.args().at(1).pattern();
		IcApp addCall = (IcApp) consBranch.body();
		IcVarLocal hdRef = (IcVarLocal) addCall.args().at(0);
		IcCnst one = (IcCnst) addCall.args().at(1);

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
