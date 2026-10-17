package zlk.test.phase.nameeval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import zlk.compiler.id.Id;
import zlk.compiler.ir.idcalc.IcCaseBranch;
import zlk.compiler.ir.idcalc.IcExp;
import zlk.compiler.ir.idcalc.IcModule;
import zlk.compiler.ir.idcalc.IcPattern;
import zlk.compiler.ir.typing.Ctor;
import zlk.compiler.ir.typing.Type;
import zlk.util.collection.Seq;
import zlk.util.tester.DumpOnFailureWatcher;
import zlk.util.tester.ModuleTester;
import zlk.util.tester.ModuleTester.CompileLevel;

@ExtendWith(DumpOnFailureWatcher.class)
public class NameEvaluatorTest {
	@Test
	void aliasAnnotationsResolveToTheSameStructuralFunctionType() {
		var module = new ModuleTester("""
				type alias YX = { y : Bool, x : I32 }
				direct : YX -> YX
				direct value = value
				eta : YX -> YX
				eta = \\value -> value
				plain : YX
				plain = { x = 10, y = True }
				""", CompileLevel.NAME_EVAL);
		IcModule ic = module.getIdcalcModule();
		assertEquals(ic.decls().head().anno(), ic.decls().at(1).anno());
		var lambda = assertInstanceOf(IcExp.IcLamb.class, ic.decls().at(1).body());
		assertEquals(1, lambda.args().size());
		assertEquals("{ x : I32, y : Bool }", ic.decls().at(2).anno().orElseThrow().buildString());
	}

	@Test
	void recursiveConstructorPatternsRetainIndependentResolvedBinders() {
		var module = new ModuleTester("""
				type alias YX = { y : Bool, x : I32 }
				type Node a = End | Next a (Node a)
				type alias F = Node YX -> { x : I32, y : Bool }
				holder : { make : F }
				holder = { make = \\(Next first (Next second _)) -> first }
				""", CompileLevel.NAME_EVAL);
		IcModule ic = module.getIdcalcModule();
		var record = assertInstanceOf(IcExp.IcRecord.class, ic.decls().head().body());
		var lambda = assertInstanceOf(IcExp.IcLamb.class, record.fields().head().value());
		var outer = assertInstanceOf(IcPattern.Dector.class, lambda.args().head());
		var inner = assertInstanceOf(IcPattern.Dector.class, outer.args().at(1));
		var first = assertInstanceOf(IcPattern.Var.class, outer.args().head());
		var second = assertInstanceOf(IcPattern.Var.class, inner.args().head());
		assertEquals("first", first.id().simpleName());
		assertEquals("second", second.id().simpleName());
		assertEquals(first.id(), assertInstanceOf(IcExp.IcVarLocal.class, lambda.body()).id());
	}

	@Test
	void openAndIndirectRecordAliasesRemainTypeOnly() {
		var module = new ModuleTester(
				"""
				type alias Open r = { r | value : I32 }
				type alias Indirect = Closed
				type alias Closed = { value : I32 }
				open : Open {}
				open = { value = 10 }
				indirect : Indirect
				indirect = { value = 20 }
				""", CompileLevel.NAME_EVAL);
		assertEquals(Seq.of("Main.open", "Main.indirect", "Main.Closed"),
				module.getIdcalcModule().decls().map(decl -> decl.id().canonicalName()));
		assertEquals("{ value : I32 }", module.getIdcalcModule().decls().head().anno().orElseThrow().buildString());
		assertEquals("{ value : I32 }", module.getIdcalcModule().decls().at(1).anno().orElseThrow().buildString());
	}

	@Test
	void recordAliasConstructorIsAnAppendedOrdinaryFunctionWithSharedTypes() {
		var module = new ModuleTester(
				"""
				r = YX
				type alias YX a = { y : a, x : I32 }
				""", CompileLevel.NAME_EVAL);
		IcModule ic = module.getIdcalcModule();
		assertEquals(0, ic.types().size());
		assertEquals(2, ic.decls().size());
		assertEquals(Id.intern("Main.r"), ic.decls().head().id());
		IcExp.IcVarLocal reference = assertInstanceOf(IcExp.IcVarLocal.class, ic.decls().head().body());
		var ctor = ic.decls().last();
		assertEquals(Id.intern("Main.YX"), ctor.id());
		assertEquals(ctor.id(), reference.id());
		assertEquals("a -> I32 -> { x : I32, y : a }", ctor.anno().orElseThrow().buildString());
		Type.Record result = assertInstanceOf(Type.Record.class, ctor.anno().orElseThrow().dropArgs(2));
		assertSame(ctor.anno().orElseThrow().arg(0), result.fields().at(1).value());
		IcExp.IcRecord body = assertInstanceOf(IcExp.IcRecord.class, ctor.body());
		assertEquals(Seq.of("y", "x"), body.fields().map(IcExp.IcRecordField::name));
		for (int index = 0; index < ctor.args().size(); index++) {
			IcPattern.Var arg = assertInstanceOf(IcPattern.Var.class, ctor.args().at(index));
			var field = body.fields().at(index);
			assertEquals(Id.intern(ctor.id(), field.name()), arg.id());
			assertEquals(arg.id(), assertInstanceOf(IcExp.IcVarLocal.class, field.value()).id());
			assertEquals(arg.loc(), field.loc());
		}
	}

	@Test
	void emptyAliasBodyCanBePrettyPrinted() {
		var module = new ModuleTester("type alias Empty = {}", CompileLevel.NAME_EVAL);
		assertEquals("{}", module.getIdcalcModule().decls().head().body().buildString());
	}

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
		Ctor ctor = ic.types().head().ctors().head();
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
