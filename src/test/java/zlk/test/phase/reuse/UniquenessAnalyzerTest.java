package zlk.test.phase.reuse;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import zlk.common.ConstValue;
import zlk.common.Location;
import zlk.common.Type;
import zlk.common.id.Id;
import zlk.ir.reuse.LocalVar;
import zlk.ir.reuse.own.OwnBlock;
import zlk.ir.reuse.own.OwnBranch;
import zlk.ir.reuse.own.OwnFunDecl;
import zlk.ir.reuse.own.OwnPattern;
import zlk.ir.reuse.own.OwnRhs;
import zlk.ir.reuse.own.OwnStmt;
import zlk.ir.reuse.own.OwnUse;
import zlk.ir.reuse.own.Ownership;
import zlk.ir.reuse.own.UseMode;
import zlk.ir.reuse.plan.Uniqueness;
import zlk.ir.reuse.plan.UniquenessFacts;
import zlk.phase.reuse.UniquenessAnalyzer;
import zlk.util.collection.Seq;

public class UniquenessAnalyzerTest {
	private static final Location NO_LOC = Location.noLocation();
	private static final Type RECORD = new Type.Record(Seq.of());

	@Test
	void makeRecordIsUniqueAtFollowingUseButNotAtItsOwnSite() {
		LocalVar record = local(0, RECORD);
		LocalVar read = local(1, Type.I32);
		OwnFunDecl function = function(
				Seq.of(
						bind(record, new OwnRhs.MakeRecord(Seq.of())),
						bind(read, new OwnRhs.RecordGet(use(record, UseMode.BORROW), "x"))),
				read,
				2);

		UniquenessFacts facts = UniquenessAnalyzer.analyze(function);

		assertEquals(Uniqueness.UNKNOWN, facts.getUniquenessAt(record, record));
		assertEquals(Uniqueness.UNIQUE, facts.getUniquenessAt(read, record));
	}

	@Test
	void dupMakesAUniqueValueUnknown() {
		LocalVar record = local(0, RECORD);
		LocalVar read = local(1, Type.I32);
		OwnFunDecl function = function(
				Seq.of(
						bind(record, new OwnRhs.MakeRecord(Seq.of())),
						new OwnStmt.Dup(record, NO_LOC),
						bind(read, new OwnRhs.RecordGet(use(record, UseMode.BORROW), "x"))),
				read,
				2);

		UniquenessFacts facts = UniquenessAnalyzer.analyze(function);

		assertEquals(Uniqueness.UNKNOWN, facts.getUniquenessAt(read, record));
	}

	@Test
	void ifKeepsOnlyValuesUniqueInBothBranches() {
		LocalVar record = local(0, RECORD);
		LocalVar condition = named(1, "condition", Type.BOOL);
		LocalVar thenResult = local(2, Type.I32);
		LocalVar elseResult = local(3, Type.I32);
		LocalVar choice = local(4, Type.I32);
		LocalVar read = local(5, Type.I32);
		OwnBlock thenBlock = block(
				Seq.of(
						new OwnStmt.Dup(record, NO_LOC),
						bind(thenResult, new OwnRhs.Cnst(new ConstValue.I32(1)))),
				thenResult);
		OwnBlock elseBlock = block(
				Seq.of(bind(elseResult, new OwnRhs.Cnst(new ConstValue.I32(2)))),
				elseResult);
		OwnFunDecl function = function(
				Seq.of(
						bind(record, new OwnRhs.MakeRecord(Seq.of())),
						bind(choice, new OwnRhs.If(
								use(condition, UseMode.BORROW), thenBlock, elseBlock)),
						bind(read, new OwnRhs.RecordGet(use(record, UseMode.BORROW), "x"))),
				read,
				6);

		UniquenessFacts facts = UniquenessAnalyzer.analyze(function);

		assertEquals(Uniqueness.UNKNOWN, facts.getUniquenessAt(read, record));
	}

	@Test
	void caseIntersectsUniquenessAcrossAllBranches() {
		LocalVar record = local(0, RECORD);
		LocalVar target = named(1, "target", Type.BOOL);
		LocalVar firstResult = local(2, Type.I32);
		LocalVar secondResult = local(3, Type.I32);
		LocalVar choice = local(4, Type.I32);
		LocalVar read = local(5, Type.I32);
		OwnBranch first = new OwnBranch(
				new OwnPattern.Wildcard(Type.BOOL, NO_LOC),
				block(Seq.of(
						new OwnStmt.Dup(record, NO_LOC),
						bind(firstResult, new OwnRhs.Cnst(new ConstValue.I32(1)))), firstResult),
				NO_LOC);
		OwnBranch second = new OwnBranch(
				new OwnPattern.Wildcard(Type.BOOL, NO_LOC),
				block(Seq.of(bind(secondResult, new OwnRhs.Cnst(new ConstValue.I32(2)))), secondResult),
				NO_LOC);
		OwnFunDecl function = function(
				Seq.of(
						bind(record, new OwnRhs.MakeRecord(Seq.of())),
						bind(choice, new OwnRhs.Case(use(target, UseMode.BORROW), Seq.of(first, second))),
						bind(read, new OwnRhs.RecordGet(use(record, UseMode.BORROW), "x"))),
				read,
				6);

		UniquenessFacts facts = UniquenessAnalyzer.analyze(function);

		assertEquals(Uniqueness.UNKNOWN, facts.getUniquenessAt(read, record));
	}

	@Test
	void recordUpdateResultIsUnique() {
		LocalVar target = named(0, "target", RECORD);
		LocalVar update = local(1, RECORD);
		LocalVar read = local(2, Type.I32);
		OwnFunDecl function = function(
				Seq.of(
						bind(update, new OwnRhs.RecordUpdate(use(target, UseMode.TAKE), Seq.of())),
						bind(read, new OwnRhs.RecordGet(use(update, UseMode.BORROW), "x"))),
				read,
				3);

		UniquenessFacts facts = UniquenessAnalyzer.analyze(function);

		assertEquals(Uniqueness.UNIQUE, facts.getUniquenessAt(read, update));
	}

	private static OwnFunDecl function(Seq<OwnStmt> stmts, LocalVar result, int localIdSize) {
		return new OwnFunDecl(
				Id.intern("Main.test"),
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
