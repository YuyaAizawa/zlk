package zlk.test.phase.reuse;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import zlk.compiler.id.Id;
import zlk.compiler.ir.ConstValue;
import zlk.compiler.ir.clcalc.CcExp;
import zlk.compiler.ir.clcalc.CcFunDecl;
import zlk.compiler.ir.clcalc.CcModule;
import zlk.compiler.ir.reuse.LocalSet;
import zlk.compiler.ir.reuse.LocalVar;
import zlk.compiler.ir.reuse.anf.AnfRhs;
import zlk.compiler.ir.reuse.own.OwnBlock;
import zlk.compiler.ir.reuse.own.OwnFunDecl;
import zlk.compiler.ir.reuse.own.OwnModule;
import zlk.compiler.ir.reuse.own.OwnRhs;
import zlk.compiler.ir.reuse.own.OwnStmt;
import zlk.compiler.ir.reuse.own.OwnUse;
import zlk.compiler.ir.reuse.own.Ownership;
import zlk.compiler.ir.reuse.own.UseMode;
import zlk.compiler.ir.reuse.plan.ReusePlan;
import zlk.compiler.ir.reuse.plan.UniquenessFacts;
import zlk.compiler.ir.typing.RecordField;
import zlk.compiler.ir.typing.Type;
import zlk.compiler.phase.reuse.AnfConverter;
import zlk.compiler.phase.reuse.OwnershipElaborator;
import zlk.compiler.phase.reuse.RecordUpdatePlanner;
import zlk.compiler.phase.reuse.ReusePlanner;
import zlk.compiler.phase.reuse.UniquenessAnalyzer;
import zlk.compiler.source.Location;
import zlk.util.collection.Seq;

public class RecordUpdatePlannerTest {
	@Test
	void selectsUpdateOfFreshRecordThroughAnfAndOwnershipLowering() {
		var loc = Location.noLocation();
		var type = new Type.Record(Seq.of(new RecordField<>("x", Type.I32), new RecordField<>("y", Type.BOOL)));
		CcExp literal = new CcExp.CcRecord(Seq.of(
				new CcExp.CcRecordField("y", new CcExp.CcCnst(ConstValue.TRUE, loc), loc),
				new CcExp.CcRecordField("x", new CcExp.CcCnst(new ConstValue.I32(10), loc), loc)), type, loc);
		CcExp body = new CcExp.CcRecordUpdate(literal,
				Seq.of(new CcExp.CcRecordField("x", new CcExp.CcCnst(new ConstValue.I32(20), loc), loc)), type, loc);
		var source = new CcModule("Main", Seq.of(), Seq.of(new CcFunDecl(Id.intern("Main.value"), Seq.of(), body, loc)));
		var anf = AnfConverter.convert(source);
		var binds = anf.funcs().head().body().binds();
		var input = assertInstanceOf(AnfRhs.RecordUpdate.class, binds.at(4).rhs()).target();
		var own = OwnershipElaborator.convert(anf);
		var fun = own.funcs().head();
		var update = (OwnStmt.Bind) fun.body().stmts().findFirst(stmt ->
				stmt instanceof OwnStmt.Bind bind && bind.rhs() instanceof OwnRhs.RecordUpdate).orElseThrow();
		var facts = UniquenessAnalyzer.analyze(fun);
		assertTrue(facts.isUniqueAt(update.dst(), input));
		assertTrue(RecordUpdatePlanner.plan(fun, facts).contains(update.dst()));
	}

	private static final Location NO_LOC = Location.noLocation();
	private static final Type RECORD = new Type.Record(Seq.of());

	@Test
	void selectsTakenUpdateOfAUniqueRecord() {
		LocalVar record = local(0, RECORD);
		LocalVar update = local(1, RECORD);
		OwnFunDecl function = function(
				"selected",
				Seq.of(
						bind(record, new OwnRhs.MakeRecord(Seq.of())),
						bind(update, new OwnRhs.RecordUpdate(use(record, UseMode.TAKE), Seq.of()))),
				update,
				2);

		LocalSet selected = plan(function);

		assertTrue(selected.contains(update));
	}

	@Test
	void rejectsUpdateAfterTheTargetWasDuplicated() {
		LocalVar record = local(0, RECORD);
		LocalVar update = local(1, RECORD);
		OwnFunDecl function = function(
				"duplicated",
				Seq.of(
						bind(record, new OwnRhs.MakeRecord(Seq.of())),
						new OwnStmt.Dup(record, NO_LOC),
						bind(update, new OwnRhs.RecordUpdate(use(record, UseMode.TAKE), Seq.of()))),
				update,
				2);

		assertFalse(plan(function).contains(update));
	}

	@Test
	void rejectsBorrowedUpdateTarget() {
		LocalVar record = local(0, RECORD);
		LocalVar update = local(1, RECORD);
		OwnFunDecl function = function(
				"borrowed",
				Seq.of(
						bind(record, new OwnRhs.MakeRecord(Seq.of())),
						bind(update, new OwnRhs.RecordUpdate(use(record, UseMode.BORROW), Seq.of()))),
				update,
				2);

		assertFalse(plan(function).contains(update));
	}

	@Test
	void selectsUpdateInsideConditionalBranch() {
		LocalVar condition = named(0, "condition", Type.BOOL);
		LocalVar record = local(1, RECORD);
		LocalVar update = local(2, RECORD);
		LocalVar alternative = local(3, RECORD);
		LocalVar result = local(4, RECORD);
		OwnBlock thenBlock = block(
				Seq.of(
						bind(record, new OwnRhs.MakeRecord(Seq.of())),
						bind(update, new OwnRhs.RecordUpdate(use(record, UseMode.TAKE), Seq.of()))),
				update);
		OwnBlock elseBlock = block(
				Seq.of(bind(alternative, new OwnRhs.MakeRecord(Seq.of()))),
				alternative);
		OwnFunDecl function = function(
				"nested",
				Seq.of(bind(result, new OwnRhs.If(
						use(condition, UseMode.BORROW), thenBlock, elseBlock))),
				result,
				5);

		assertTrue(plan(function).contains(update));
	}

	@Test
	void keepsSelectedSitesSeparatedByFunction() {
		LocalVar firstRecord = local(0, RECORD);
		LocalVar firstUpdate = local(1, RECORD);
		OwnFunDecl selectedFunction = function(
				"selectedFunction",
				Seq.of(
						bind(firstRecord, new OwnRhs.MakeRecord(Seq.of())),
						bind(firstUpdate, new OwnRhs.RecordUpdate(
								use(firstRecord, UseMode.TAKE), Seq.of()))),
				firstUpdate,
				2);
		LocalVar secondRecord = local(0, RECORD);
		LocalVar secondUpdate = local(1, RECORD);
		OwnFunDecl rejectedFunction = function(
				"rejectedFunction",
				Seq.of(
						bind(secondRecord, new OwnRhs.MakeRecord(Seq.of())),
						new OwnStmt.Dup(secondRecord, NO_LOC),
						bind(secondUpdate, new OwnRhs.RecordUpdate(
								use(secondRecord, UseMode.TAKE), Seq.of()))),
				secondUpdate,
				2);
		ReusePlan plan = ReusePlanner.plan(new OwnModule(
				"Main", Seq.of(), Seq.of(selectedFunction, rejectedFunction)));

		assertTrue(plan.isInplaceRecordUpdate(selectedFunction.id(), firstUpdate));
		assertFalse(plan.isInplaceRecordUpdate(rejectedFunction.id(), secondUpdate));
	}

	private static LocalSet plan(OwnFunDecl function) {
		UniquenessFacts facts = UniquenessAnalyzer.analyze(function);
		return RecordUpdatePlanner.plan(function, facts);
	}

	private static OwnFunDecl function(
			String name,
			Seq<OwnStmt> stmts,
			LocalVar result,
			int localIdSize
	) {
		return new OwnFunDecl(
				Id.intern("Main." + name),
				Seq.of(),
				block(stmts, result),
				localIdSize,
				NO_LOC);
	}

	private static OwnBlock block(Seq<OwnStmt> stmts, LocalVar result) {
		return new OwnBlock(stmts, use(result, UseMode.TAKE), NO_LOC);
	}

	private static OwnStmt.Bind bind(LocalVar dst, OwnRhs rhs) {
		return new OwnStmt.Bind(dst, Ownership.OWNED, rhs, NO_LOC);
	}

	private static OwnUse use(LocalVar var, UseMode mode) {
		return new OwnUse(var, mode);
	}

	private static LocalVar named(int localId, String name, Type type) {
		return new LocalVar(localId, Optional.of(Id.intern("Main.test." + name)), type);
	}

	private static LocalVar local(int localId, Type type) {
		return new LocalVar(localId, Optional.empty(), type);
	}
}
