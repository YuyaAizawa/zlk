package zlk.test.phase.anfconv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.Test;

import zlk.common.ConstValue;
import zlk.common.Location;
import zlk.common.RecordField;
import zlk.common.Type;
import zlk.common.id.Id;
import zlk.ir.anf.AnfBlock;
import zlk.ir.clcalc.CcCtor;
import zlk.ir.clcalc.CcExp;
import zlk.ir.clcalc.CcFunDecl;
import zlk.ir.clcalc.CcModule;
import zlk.ir.clcalc.CcTypeDecl;
import zlk.ir.anf.AnfAtom;
import zlk.ir.anf.AnfBind;
import zlk.ir.anf.AnfRhs;
import zlk.phase.anfconv.AnfConverter;
import zlk.util.collection.Seq;

public class AnfConverterTest {
	private static final Location NO_LOC = Location.noLocation();

	@Test
	void convertsWholeModule() {
		Id typeId = Id.intern("Main.Box");
		Id ctorId = Id.intern("Main.Box.Box");
		Id funId = Id.intern("Main.answer");
		CcModule source = new CcModule(
				"Main",
				Seq.of(new CcTypeDecl(
						typeId,
						Seq.of(new CcCtor(ctorId, Seq.of(Type.I32), NO_LOC)),
						NO_LOC)),
				Seq.of(new CcFunDecl(
						funId,
						Seq.of(),
						new CcExp.CcCnst(new ConstValue.I32(42), NO_LOC),
						NO_LOC)));
		var actual = new AnfConverter(source).convert();

		assertEquals("Main", actual.name());
		assertEquals(1, actual.types().size());
		assertEquals(typeId, actual.types().head().id());
		assertEquals(ctorId, actual.types().head().ctors().head().id());
		assertEquals(Seq.of(Type.I32), actual.types().head().ctors().head().args());
		assertEquals(1, actual.funcs().size());
		assertEquals(funId, actual.funcs().head().id());
		AnfAtom.Cnst result = assertInstanceOf(
				AnfAtom.Cnst.class,
				actual.funcs().head().body().result());
		assertEquals(new ConstValue.I32(42), result.value());
	}

	@Test
	void bindsNestedApplicationsInEvaluationOrder() {
		Id funId = Id.intern("Main.result");
		Id firstId = Id.intern("Main.first");
		Id secondId = Id.intern("Main.second");
		Id addId = Id.intern("Basic.add");
		CcExp first = new CcExp.CcDirectApp(
				firstId, Seq.of(), Type.I32, NO_LOC);
		CcExp second = new CcExp.CcDirectApp(
				secondId, Seq.of(), Type.I32, NO_LOC);
		CcExp body = new CcExp.CcDirectApp(
				addId, Seq.of(first, second), Type.I32, NO_LOC);
		CcModule source = new CcModule(
				"Main",
				Seq.of(),
				Seq.of(new CcFunDecl(funId, Seq.of(), body, NO_LOC)));

		var actual = new AnfConverter(source).convert();
		var block = actual.funcs().head().body();

		assertEquals(3, block.binds().size());
		AnfBind firstBind = assertInstanceOf(
				AnfBind.class, block.binds().at(0));
		AnfBind secondBind = assertInstanceOf(
				AnfBind.class, block.binds().at(1));
		AnfBind resultBind = assertInstanceOf(
				AnfBind.class, block.binds().at(2));
		assertEquals(firstId, assertInstanceOf(AnfRhs.DirectApp.class, firstBind.rhs()).fun());
		assertEquals(secondId, assertInstanceOf(AnfRhs.DirectApp.class, secondBind.rhs()).fun());
		AnfRhs.DirectApp resultRhs = assertInstanceOf(AnfRhs.DirectApp.class, resultBind.rhs());
		assertEquals(addId, resultRhs.fun());
		assertEquals(firstBind.dst(), resultRhs.args().at(0));
		assertEquals(secondBind.dst(), resultRhs.args().at(1));
		assertEquals(resultBind.dst(), block.result());
	}

	@Test
	void keepsConditionalBranchesInSeparateBlocks() {
		Id funId = Id.intern("Main.choose");
		Id thenId = Id.intern("Main.thenValue");
		Id elseId = Id.intern("Main.elseValue");
		CcExp body = new CcExp.CcIf(
				new CcExp.CcCnst(ConstValue.TRUE, NO_LOC),
				new CcExp.CcDirectApp(thenId, Seq.of(), Type.I32, NO_LOC),
				new CcExp.CcDirectApp(elseId, Seq.of(), Type.I32, NO_LOC),
				Type.I32,
				NO_LOC);
		CcModule source = new CcModule(
				"Main",
				Seq.of(),
				Seq.of(new CcFunDecl(funId, Seq.of(), body, NO_LOC)));

		var block = new AnfConverter(source)
				.convert().funcs().head().body();

		assertEquals(1, block.binds().size());
		AnfBind resultBind = assertInstanceOf(
				AnfBind.class, block.binds().head());
		AnfRhs.If ifRhs = assertInstanceOf(AnfRhs.If.class, resultBind.rhs());
		assertInstanceOf(AnfAtom.Cnst.class, ifRhs.condition());
		assertEquals(1, ifRhs.thenBlock().binds().size());
		assertEquals(1, ifRhs.elseBlock().binds().size());
		assertEquals(
				thenId,
				assertInstanceOf(
						AnfRhs.DirectApp.class,
						assertInstanceOf(
								AnfBind.class,
								ifRhs.thenBlock().binds().head()).rhs()).fun());
		assertEquals(
				elseId,
				assertInstanceOf(
						AnfRhs.DirectApp.class,
						assertInstanceOf(
								AnfBind.class,
								ifRhs.elseBlock().binds().head()).rhs()).fun());
		assertEquals(resultBind.dst(), block.result());
	}

	@Test
	void preservesRecordEvaluationOrderAndNamedFieldAssociation() {
		Id funId = Id.intern("Main.recordValue");
		Id baseId = Id.intern("Main.recordValue.base");
		Id updatedId = Id.intern("Main.recordValue.updated");
		Id yValueId = Id.intern("Main.yValue");
		Id xValueId = Id.intern("Main.xValue");
		Id newXId = Id.intern("Main.newX");
		Type recordType = new Type.Record(Seq.of(
				new RecordField<>("y", Type.I32),
				new RecordField<>("x", Type.I32)));
		CcExp record = new CcExp.CcRecord(
				Seq.of(
						new CcExp.CcRecordField(
								"y",
								new CcExp.CcDirectApp(yValueId, Seq.of(), Type.I32, NO_LOC),
								NO_LOC),
						new CcExp.CcRecordField(
								"x",
								new CcExp.CcDirectApp(xValueId, Seq.of(), Type.I32, NO_LOC),
								NO_LOC)),
				recordType,
				NO_LOC);
		CcExp update = new CcExp.CcRecordUpdate(
				new CcExp.CcVar(baseId, recordType, NO_LOC),
				Seq.of(new CcExp.CcRecordField(
						"x",
						new CcExp.CcDirectApp(newXId, Seq.of(), Type.I32, NO_LOC),
						NO_LOC)),
				recordType,
				NO_LOC);
		CcExp body = new CcExp.CcLet(
				baseId,
				record,
				new CcExp.CcLet(
						updatedId,
						update,
						new CcExp.CcRecordAccess(
								new CcExp.CcVar(updatedId, recordType, NO_LOC),
								"x",
								Type.I32,
								NO_LOC),
						recordType,
						NO_LOC),
				Type.I32,
				NO_LOC);
		CcModule source = new CcModule(
				"Main",
				Seq.of(),
				Seq.of(new CcFunDecl(funId, Seq.of(), body, NO_LOC)));

		AnfBlock block = new AnfConverter(source)
				.convert().funcs().head().body();

		assertEquals(yValueId, directAppAt(block, 0).fun());
		assertEquals(xValueId, directAppAt(block, 1).fun());
		AnfRhs.MakeRecord makeRecord = assertInstanceOf(
				AnfRhs.MakeRecord.class,
				assertInstanceOf(AnfBind.class, block.binds().at(2)).rhs());
		assertEquals("y", makeRecord.fields().at(0).name());
		assertEquals("x", makeRecord.fields().at(1).name());
		assertEquals(
				assertInstanceOf(AnfBind.class, block.binds().at(0)).dst(),
				makeRecord.fields().at(0).value());
		assertEquals(
				assertInstanceOf(AnfBind.class, block.binds().at(1)).dst(),
				makeRecord.fields().at(1).value());
		assertEquals(newXId, directAppAt(block, 4).fun());
		assertInstanceOf(
				AnfRhs.RecordUpdate.class,
				assertInstanceOf(AnfBind.class, block.binds().at(5)).rhs());
		assertInstanceOf(
				AnfRhs.RecordGet.class,
				assertInstanceOf(AnfBind.class, block.binds().last()).rhs());
	}

	@Test
	void lowersClosureConstructionAndApplicationInEvaluationOrder() {
		Id funId = Id.intern("Main.useClosure");
		Id codeId = Id.intern("Main.closureCode");
		Id captureId = Id.intern("Main.capture");
		Id argumentId = Id.intern("Main.argument");
		Type closureType = Type.arrow(Type.I32, Type.I32);
		CcExp closure = new CcExp.CcMkCls(
				codeId,
				Seq.of(new CcExp.CcDirectApp(captureId, Seq.of(), Type.I32, NO_LOC)),
				closureType,
				NO_LOC);
		CcExp body = new CcExp.CcClosureApp(
				closure,
				Seq.of(new CcExp.CcDirectApp(argumentId, Seq.of(), Type.I32, NO_LOC)),
				Type.I32,
				NO_LOC);
		CcModule source = new CcModule(
				"Main",
				Seq.of(),
				Seq.of(new CcFunDecl(funId, Seq.of(), body, NO_LOC)));

		AnfBlock block = new AnfConverter(source)
				.convert().funcs().head().body();

		assertEquals(captureId, directAppAt(block, 0).fun());
		assertInstanceOf(
				AnfRhs.MakeClosure.class,
				assertInstanceOf(AnfBind.class, block.binds().at(1)).rhs());
		assertEquals(argumentId, directAppAt(block, 2).fun());
		assertInstanceOf(
				AnfRhs.ClosureApp.class,
				assertInstanceOf(AnfBind.class, block.binds().at(3)).rhs());
	}

	private static AnfRhs.DirectApp directAppAt(AnfBlock block, int index) {
		return assertInstanceOf(
				AnfRhs.DirectApp.class,
				assertInstanceOf(AnfBind.class, block.binds().at(index)).rhs());
	}
}
