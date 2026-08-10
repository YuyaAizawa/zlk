package zlk.test.phase.nameeval;

import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import org.junit.jupiter.api.Test;
import zlk.common.Type;
import zlk.common.id.Id;
import zlk.ir.idcalc.IcCaseBranch;
import zlk.ir.idcalc.IcCtor;
import zlk.ir.idcalc.IcExp;
import zlk.ir.idcalc.IcModule;
import zlk.ir.idcalc.IcPattern;
import zlk.util.collection.Seq;
import zlk.util.tester.DumpOnFailureWatcher;
import zlk.util.tester.ModuleTester;
import zlk.util.tester.ModuleTester.CompileLevel;

@ExtendWith(DumpOnFailureWatcher.class)
public class NameEvaluatorTest {

	@Test
	void ctorSignatureAndIcCtorArgsShareResolvedArguments() {
		// 改善G characterization: ADT constructor引数型は一度だけ解決され，
		// value constructor signature (IcVarCtor.type) と IcCtor.args が
		// 同じ解決結果から供給される．aliasをconstructor引数に持つADTで，
		// IcCtor.args と IcVarCtor.type().flatten() の引数部分が同一の
		// semantic Type であることを確認する．
		var module = new ModuleTester(
				"""
				type alias Box a = { value : a }
				type Wrapped a = Wrapped (Box a)
				wrapped = Wrapped { value = 1 }
				""", CompileLevel.NAME_EVAL);

		IcModule ic = module.getIdcalcModule();
		IcCtor ctor = ic.types().head().ctors().head();
		// IcCtor.args: [Box a展開後のRecord]
		Seq<Type> ctorArgs = ctor.args();

		// value宣言 wrapped の body は IcApp(IcVarCtor(Wrapped), [record])．
		// IcVarCtor.type は constructor signature = fromSeq(args ++ [retTy])．
		IcExp body = ic.decls().head().body();
		IcExp.IcApp app = assertInstanceOf(IcExp.IcApp.class, body);
		IcExp.IcVarCtor varCtor = assertInstanceOf(IcExp.IcVarCtor.class, app.fun());
		// flatten() は [arg1, ..., retTy]．dropLast で引数部分を得る．
		Seq<Type> sigArgs = varCtor.type().flatten().dropLast();

		assertEquals(ctorArgs, sigArgs);
	}

	@Test
	void casesInSameScopeUseDistinctBranchScopes() {
		var module = new ModuleTester(
				"""
				type Choice = First I32 | Second I32
				choose left right =
				  if True then
				    case left of
				      First x -> x
				      Second y -> y
				  else
				    case right of
				      First x -> x
				      Second y -> y
				""", CompileLevel.NAME_EVAL);

		IcExp.IcIf body = assertInstanceOf(
				IcExp.IcIf.class,
				module.getIdcalcModule().decls().head().body());
		IcExp.IcCase first = assertInstanceOf(IcExp.IcCase.class, body.exp1());
		IcExp.IcCase second = assertInstanceOf(IcExp.IcCase.class, body.exp2());

		assertEquals(Id.intern("Main.choose._case1_1.x"), binderId(first.branches().at(0)));
		assertEquals(Id.intern("Main.choose._case1_2.y"), binderId(first.branches().at(1)));
		assertEquals(Id.intern("Main.choose._case2_1.x"), binderId(second.branches().at(0)));
		assertEquals(Id.intern("Main.choose._case2_2.y"), binderId(second.branches().at(1)));
	}

	private static Id binderId(IcCaseBranch branch) {
		IcPattern.Dector pattern = assertInstanceOf(IcPattern.Dector.class, branch.pattern());
		return assertInstanceOf(IcPattern.Var.class, pattern.args().head()).id();
	}
}
