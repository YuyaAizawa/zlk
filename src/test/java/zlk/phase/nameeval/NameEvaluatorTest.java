package zlk.phase.nameeval;

import org.junit.jupiter.api.extension.ExtendWith;
import zlk.tester.DumpOnFailureWatcher;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import zlk.common.Type;
import zlk.ir.idcalc.IcCtor;
import zlk.ir.idcalc.IcExp;
import zlk.ir.idcalc.IcModule;
import zlk.tester.ModuleTester;
import zlk.tester.ModuleTester.CompileLevel;
import zlk.util.collection.Seq;

@ExtendWith(DumpOnFailureWatcher.class)
public class NameEvaluatorTest {
	@Test
	void letInThenBranchDoesNotLeakToElseBranch() {
		// then 側の let で宣言した名前が else 側から見えてはならない．
		// 退出後に binding を破棄する一時 frame の検証．
		assertThrows(RuntimeException.class, () -> new ModuleTester("""
				f n =
				  if isZero n then
				    let
				      one = 1
				    in
				      one
				  else
				    one
				""", CompileLevel.NAME_EVAL));
	}

	@Test
	void ctorSignatureAndIcCtorArgsShareResolvedArguments() {
		// 改善G characterization: ADT constructor引数型は一度だけ解決され，
		// value constructor signature (IcVarCtor.type) と IcCtor.args が
		// 同じ解決結果から供給される．aliasをconstructor引数に持つADTで，
		// IcCtor.args と IcVarCtor.type().flatten() の引数部分が同一の
		// semantic Type であることを確認する．
		var module = new ModuleTester("""
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
}
