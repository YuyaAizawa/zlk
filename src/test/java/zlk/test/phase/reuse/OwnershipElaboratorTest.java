package zlk.test.phase.reuse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import zlk.compiler.id.Id;
import zlk.compiler.ir.ConstValue;
import zlk.compiler.ir.reuse.LocalVar;
import zlk.compiler.ir.reuse.anf.AnfBind;
import zlk.compiler.ir.reuse.anf.AnfBlock;
import zlk.compiler.ir.reuse.anf.AnfBranch;
import zlk.compiler.ir.reuse.anf.AnfFunDecl;
import zlk.compiler.ir.reuse.anf.AnfModule;
import zlk.compiler.ir.reuse.anf.AnfPattern;
import zlk.compiler.ir.reuse.anf.AnfRhs;
import zlk.compiler.ir.reuse.own.OwnFunDecl;
import zlk.compiler.ir.reuse.own.OwnRhs;
import zlk.compiler.ir.reuse.own.OwnStmt;
import zlk.compiler.ir.reuse.own.OwnUse;
import zlk.compiler.ir.reuse.own.UseMode;
import zlk.compiler.ir.typing.Type;
import zlk.compiler.phase.reuse.OwnershipElaborator;
import zlk.compiler.source.Location;
import zlk.util.collection.Seq;

public class OwnershipElaboratorTest {
	private static final Location NO_LOC = Location.noLocation();
	private static final Id FUN = Id.intern("Main.test");
	private static final Id CALL = Id.intern("Main.call");

	@Test
	void insertsDupBeforeAnOwnedValueIsTakenMoreThanOnce() {
		LocalVar x = named(0, "x", Type.I32);
		LocalVar first = local(1, Type.I32);
		LocalVar result = local(2, Type.I32);
		OwnFunDecl actual = elaborate(
				Seq.of(pattern(x)),
				Seq.of(
						bind(first, new AnfRhs.DirectApp(CALL, Seq.of(x))),
						bind(result, new AnfRhs.DirectApp(CALL, Seq.of(first, x)))),
				result,
				3);

		assertEquals(3, actual.body().stmts().size());
		assertEquals(x, assertInstanceOf(OwnStmt.Dup.class, actual.body().stmts().at(0)).var());
		assertEquals(first, assertInstanceOf(OwnStmt.Bind.class, actual.body().stmts().at(1)).dst());
		assertEquals(result, assertInstanceOf(OwnStmt.Bind.class, actual.body().stmts().at(2)).dst());
	}

	@Test
	void dropsAnOwnedValueAfterItsLastBorrow() {
		Type recordType = new Type.Record(Seq.of());
		LocalVar record = named(0, "record", recordType);
		LocalVar field = local(1, Type.I32);
		OwnFunDecl actual = elaborate(
				Seq.of(pattern(record)),
				Seq.of(bind(field, new AnfRhs.RecordGet(record, "x"))),
				field,
				2);

		OwnStmt.Bind get = assertInstanceOf(OwnStmt.Bind.class, actual.body().stmts().at(0));
		OwnUse target = assertInstanceOf(OwnRhs.RecordGet.class, get.rhs()).target();
		assertEquals(UseMode.BORROW, target.mode());
		assertEquals(record, assertInstanceOf(OwnStmt.Drop.class, actual.body().stmts().at(1)).var());
	}

	@Test
	void removesUnusedBindings() {
		LocalVar unused = local(0, Type.I32);
		LocalVar result = local(1, Type.I32);
		OwnFunDecl actual = elaborate(
				Seq.of(),
				Seq.of(
						bind(unused, new AnfRhs.Cnst(new ConstValue.I32(1))),
						bind(result, new AnfRhs.Cnst(new ConstValue.I32(2)))),
				result,
				2);

		assertEquals(1, actual.body().stmts().size());
		assertEquals(result, assertInstanceOf(OwnStmt.Bind.class, actual.body().stmts().head()).dst());
	}

	@Test
	void marksRecordUpdateInputsAsTaken() {
		Type recordType = new Type.Record(Seq.of());
		LocalVar record = named(0, "record", recordType);
		LocalVar value = named(1, "value", Type.I32);
		LocalVar result = local(2, recordType);
		OwnFunDecl actual = elaborate(
				Seq.of(pattern(record), pattern(value)),
				Seq.of(bind(result, new AnfRhs.RecordUpdate(
						record,
						Seq.of(new AnfRhs.AnfRecordField("x", value))))),
				result,
				3);

		OwnRhs.RecordUpdate update = assertInstanceOf(
				OwnRhs.RecordUpdate.class,
				assertInstanceOf(OwnStmt.Bind.class, actual.body().stmts().head()).rhs());
		assertEquals(UseMode.TAKE, update.target().mode());
		assertEquals(UseMode.TAKE, update.fields().head().value().mode());
	}

	@Test
	void insertsBranchLocalDropsForOuterValues() {
		LocalVar condition = named(0, "condition", Type.BOOL);
		LocalVar left = named(1, "left", Type.I32);
		LocalVar right = named(2, "right", Type.I32);
		LocalVar result = local(3, Type.I32);
		AnfBlock thenBlock = new AnfBlock(Seq.of(), left, NO_LOC);
		AnfBlock elseBlock = new AnfBlock(Seq.of(), right, NO_LOC);
		OwnFunDecl actual = elaborate(
				Seq.of(pattern(condition), pattern(left), pattern(right)),
				Seq.of(bind(result, new AnfRhs.If(condition, thenBlock, elseBlock))),
				result,
				4);

		OwnRhs.If ownIf = assertInstanceOf(
				OwnRhs.If.class,
				assertInstanceOf(OwnStmt.Bind.class, actual.body().stmts().head()).rhs());
		assertEquals(right, assertInstanceOf(OwnStmt.Drop.class, ownIf.thenBlock().stmts().head()).var());
		assertEquals(left, assertInstanceOf(OwnStmt.Drop.class, ownIf.elseBlock().stmts().head()).var());
	}

	@Test
	void dropsUnusedOwnedPatternBindersAtBranchEntry() {
		Type recordType = new Type.Record(Seq.of());
		LocalVar target = named(0, "target", recordType);
		LocalVar binder = named(1, "x", Type.I32);
		LocalVar branchResult = local(2, Type.I32);
		LocalVar result = local(3, Type.I32);
		AnfBranch branch = new AnfBranch(
				new AnfPattern.Record(Seq.of(pattern(binder)), recordType, NO_LOC),
				new AnfBlock(
						Seq.of(bind(branchResult, new AnfRhs.Cnst(new ConstValue.I32(1)))),
						branchResult,
						NO_LOC),
				NO_LOC);
		OwnFunDecl actual = elaborate(
				Seq.of(pattern(target)),
				Seq.of(bind(result, new AnfRhs.Case(target, Seq.of(branch)))),
				result,
				4);

		OwnRhs.Case ownCase = assertInstanceOf(
				OwnRhs.Case.class,
				assertInstanceOf(OwnStmt.Bind.class, actual.body().stmts().head()).rhs());
		assertEquals(
				binder,
				assertInstanceOf(
						OwnStmt.Drop.class,
						ownCase.branches().head().body().stmts().head()).var());
	}

	private static OwnFunDecl elaborate(
			Seq<AnfPattern> args,
			Seq<AnfBind> binds,
			LocalVar result,
			int localIdSize
	) {
		AnfFunDecl function = new AnfFunDecl(
				FUN,
				args,
				new AnfBlock(binds, result, NO_LOC),
				localIdSize,
				NO_LOC);
		return OwnershipElaborator.convert(new AnfModule("Main", Seq.of(), Seq.of(function)))
				.funcs().head();
	}

	private static AnfBind bind(LocalVar dst, AnfRhs rhs) {
		return new AnfBind(dst, rhs, NO_LOC);
	}

	private static AnfPattern.Var pattern(LocalVar var) {
		return new AnfPattern.Var(var, NO_LOC);
	}

	private static LocalVar named(int localId, String name, Type type) {
		return new LocalVar(localId, Optional.of(Id.intern("Main.test." + name)), type);
	}

	private static LocalVar local(int localId, Type type) {
		return new LocalVar(localId, Optional.empty(), type);
	}
}
