package zlk.recon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.function.IntFunction;

import org.junit.jupiter.api.Test;

import zlk.common.RecordField;
import zlk.common.Type;
import zlk.recon.FlatType.CtorApp1;
import zlk.recon.FlatType.Record1;
import zlk.recon.FlatType.Row1;
import zlk.recon.constraint.Content.Structure;
import zlk.recon.constraint.RcType;
import zlk.recon.constraint.RcType.Inst;
import zlk.recon.constraint.RcType.RecordN;
import zlk.util.collection.Seq;

class RowInferenceTest {

	@Test
	void unifyPreservesRowKind() {
		FreshFlex fresh = new FreshFlex();
		Variable a = fresh.getVariable(Variable.Kind.ROW);
		Variable b = fresh.getVariable(Variable.Kind.ROW);
		Unify.unify(a, b);
		assertEquals(Variable.Kind.ROW, a.kind());
		assertEquals(Variable.Kind.ROW, b.kind());
	}

	@Test
	void unifyRejectsDifferentKinds() {
		FreshFlex fresh = new FreshFlex();
		Variable a = fresh.getVariable(Variable.Kind.TYPE);
		Variable b = fresh.getVariable(Variable.Kind.ROW);
		assertThrows(Missmatch.class, () -> Unify.unify(a, b));
	}

	@Test
	void variableStateRejectsStructureKindMismatch() {
		// TYPE root + Row1 -> reject
		assertThrows(IllegalArgumentException.class, () ->
			new VariableState(
				new Structure(new Row1(Seq.of(), Optional.empty())),
				0, Variable.Kind.TYPE));
		// ROW root + Fun1 -> reject
		FreshFlex fresh = new FreshFlex();
		Variable typeArg = fresh.getVariable(Variable.Kind.TYPE);
		Variable typeRet = fresh.getVariable(Variable.Kind.TYPE);
		assertThrows(IllegalArgumentException.class, () ->
			new VariableState(
				new Structure(new FlatType.Fun1(typeArg, typeRet)),
				0, Variable.Kind.ROW));
		// ROW root + CtorApp1 -> reject
		assertThrows(IllegalArgumentException.class, () ->
			new VariableState(
				new Structure(new CtorApp1(Type.I32.id(), Seq.of())),
				0, Variable.Kind.ROW));
		// ROW root + Record1 -> reject
		Variable rowChild = fresh.getVariable(Variable.Kind.ROW);
		assertThrows(IllegalArgumentException.class, () ->
			new VariableState(
				new Structure(new Record1(rowChild)),
				0, Variable.Kind.ROW));
	}

	@Test
	void flatStructuresRejectChildrenOfWrongKind() {
		FreshFlex fresh = new FreshFlex();
		// Record1 child must be ROW (given TYPE child -> reject)
		Variable typeVar = fresh.getVariable(Variable.Kind.TYPE);
		assertThrows(IllegalArgumentException.class, () ->
			new VariableState(
				new Structure(new Record1(typeVar)),
				0, Variable.Kind.TYPE));
		// Row1 field value must be TYPE (given ROW -> reject)
		Variable rowVar = fresh.getVariable(Variable.Kind.ROW);
		assertThrows(IllegalArgumentException.class, () ->
			new VariableState(
				new Structure(new Row1(
					Seq.of(new RecordField<>("x", rowVar)),
					Optional.empty())),
				0, Variable.Kind.ROW));
		// Row1 extension must be ROW (given TYPE -> reject)
		Variable typeExt = fresh.getVariable(Variable.Kind.TYPE);
		assertThrows(IllegalArgumentException.class, () ->
			new VariableState(
				new Structure(new Row1(
					Seq.of(),
					Optional.of(typeExt))),
				0, Variable.Kind.ROW));
	}

	@Test
	void rcTypePreservesOpenTail() {
		Type.RowVar rv = new Type.RowVar("r");
		Type.Record openRec = new Type.Record(
			Seq.of(new RecordField<>("x", Type.I32)),
			Optional.of(rv));
		FreshFlex fresh = new FreshFlex();
		Inst inst = RcType.instantiate(openRec, fresh);
		assertTrue(inst.type() instanceof RecordN);
		RecordN recN = (RecordN) inst.type();
		assertTrue(recN.extension().isPresent());
		assertEquals(Variable.Kind.ROW, recN.extension().get().kind());
		// toType preserves tail name
		Type result = recN.toType();
		assertTrue(result instanceof Type.Record);
		Type.Row resultRow = ((Type.Record) result).row();
		assertTrue(resultRow.extension().isPresent());
		assertEquals("r", resultRow.extension().get().name());
	}

	@Test
	void rcTypeRejectsSameNameAtDifferentKinds() {
		Type.Record rec = new Type.Record(
			Seq.of(new RecordField<>("x", new Type.Var("a"))),
			Optional.of(new Type.RowVar("a")));
		FreshFlex fresh = new FreshFlex();
		assertThrows(IllegalArgumentException.class,
			() -> RcType.instantiate(rec, fresh));
	}

	@Test
	void recordConversionDoesNotCreateRecursiveRow() {
		FreshFlex fresh = new FreshFlex();
		Variable i32Var = new Variable(
			new Structure(new CtorApp1(Type.I32.id(), Seq.of())),
			0, Variable.Kind.TYPE);
		Seq<RecordField<Variable>> fields = Seq.of(
			new RecordField<>("x", i32Var));
		Variable tail = fresh.getVariable(Variable.Kind.ROW, 1);
		Variable record = TypeReconstructor.buildRecordVar(
			fresh, 1, fields, Optional.of(tail));
		assertEquals(Variable.Kind.TYPE, record.kind());
		// anchorとtailは別物
		VariableState state = record.get();
		assertTrue(state.content instanceof Structure);
		Structure struct = (Structure) state.content;
		assertTrue(struct.flatType() instanceof Record1);
		Variable anchor = ((Record1) struct.flatType()).row();
		assertFalse(anchor.isSame(tail));
		assertFalse(anchor.occurs());
	}

	@Test
	void freshFlexPreservesLetRankForRowKind() {
		FreshFlex fresh = new FreshFlex();
		int letRank = 3;
		Variable v = fresh.getVariable(Variable.Kind.ROW, letRank);
		VariableState state = v.get();
		assertEquals(letRank, state.rank);
	}

	// ===== open row unification テスト群 =====

	/** TYPE-kind I32 を示すVariableを構築するヘルパ． */
	private static Variable i32Var(int rank) {
		return new Variable(
				new Structure(new CtorApp1(Type.I32.id(), Seq.of())),
				rank, Variable.Kind.TYPE);
	}

	/** TYPE-kind Bool を示すVariableを構築するヘルパ． */
	private static Variable boolVar(int rank) {
		return new Variable(
				new Structure(new CtorApp1(Type.BOOL.id(), Seq.of())),
				rank, Variable.Kind.TYPE);
	}

	/** ROW-kind rootにRow1 structureをsetするヘルパ． */
	private static Variable makeRowRoot(
			FreshFlex fresh, int rank,
			Seq<RecordField<Variable>> fields,
			Optional<Variable> ext) {
		Variable row = fresh.getVariable(Variable.Kind.ROW, rank);
		row.set(new VariableState(
				new Structure(new Row1(fields, ext)),
				rank, Variable.Kind.ROW));
		return row;
	}

	/** ROW-kind root配下のRow1(Optional empty tail)を構築するヘルパ． */
	private static Variable closedRowRoot(
			FreshFlex fresh, int rank,
			Seq<RecordField<Variable>> fields) {
		return makeRowRoot(fresh, rank, fields, Optional.empty());
	}

	/** Row1 fieldsからlabel名でfieldを検索してvalueを返すヘルパ．owning root前提． */
	private static Variable rowField(Variable rowRoot, String name) {
		VariableState s = rowRoot.get();
		Structure st = (Structure) s.content;
		Row1 r = (Row1) st.flatType();
		return r.fields().findFirst(f -> f.name().equals(name))
				.orElseThrow(() -> new AssertionError("field not found: " + name))
				.value();
	}

	/** namerはflex idを 'r' + id で命名する固定namer． */
	private static final IntFunction<String> ROWNAMER = id -> "r" + id;

	@Test
	void closedRowsUnifyRegardlessOfSourceFieldOrder() {
		FreshFlex fresh = new FreshFlex();
		int rank = 2;
		Variable left = closedRowRoot(fresh, rank, Seq.of(
				new RecordField<>("x", i32Var(rank)),
				new RecordField<>("y", boolVar(rank))));
		// canonicalize済みなので順序は正規化されるはず
		Variable right = closedRowRoot(fresh, rank, Seq.of(
				new RecordField<>("y", boolVar(rank)),
				new RecordField<>("x", i32Var(rank))));
		Unify.unify(left, right, fresh, rank);
		assertTrue(left.isSame(right));
		assertEquals(Variable.Kind.ROW, left.kind());
	}

	@Test
	void openRowUnifiesWithWiderClosedRowAndClosesTail() {
		FreshFlex fresh = new FreshFlex();
		int rank = 2;
		// open: { x:I32 | r }
		Variable openTail = fresh.getVariable(Variable.Kind.ROW, rank);
		Variable openRow = makeRowRoot(fresh, rank,
				Seq.of(new RecordField<>("x", i32Var(rank))),
				Optional.of(openTail));
		// closed wider: { x:I32, y:Bool }（tailなし）
		Variable closedWider = closedRowRoot(fresh, rank, Seq.of(
				new RecordField<>("x", i32Var(rank)),
				new RecordField<>("y", boolVar(rank))));
		Unify.unify(openRow, closedWider, fresh, rank);
		assertTrue(openRow.isSame(closedWider));
		//残差 { y:Bool } がopenTailへ束縛され，全体としてclosedになる
		VariableState tailState = openTail.get();
		assertTrue(tailState.content instanceof Structure);
		Row1 boundTail = (Row1) ((Structure) tailState.content).flatType();
		assertFalse(boundTail.isOpen());
		assertEquals(1, boundTail.fields().size());
		assertEquals("y", boundTail.fields().at(0).name());
	}

	@Test
	void oneSidedResidualIsPushedIntoOppositeTail() {
		FreshFlex fresh = new FreshFlex();
		int rank = 3;
		// left:  { x:I32 }         (closed)
		// right: { x:I32 | s }    (open) ← right tailへ { }(empty) に必要
		// rightの余分残余はないが，rightTailが { }(empty) へ束縛される
		Variable rightTail = fresh.getVariable(Variable.Kind.ROW, rank);
		Variable left = closedRowRoot(fresh, rank,
				Seq.of(new RecordField<>("x", i32Var(rank))));
		Variable right = makeRowRoot(fresh, rank,
				Seq.of(new RecordField<>("x", i32Var(rank))),
				Optional.of(rightTail));
		Unify.unify(left, right, fresh, rank);
		// rightTailはempty closed Row1へ束縛される
		VariableState tailState = rightTail.get();
		assertTrue(tailState.content instanceof Structure);
		Row1 boundTail = (Row1) ((Structure) tailState.content).flatType();
		assertFalse(boundTail.isOpen());
		assertEquals(0, boundTail.fields().size());
	}

	@Test
	void twoSidedResidualUsesFreshCommonTailAtCurrentRank() {
		FreshFlex fresh = new FreshFlex();
		int rank = 5;
		// left  : { left:Bool, x:I32 | r1 }
		// right : { right:Bool, x:I32 | r2 }
		// 両側に固有fieldがあるため，新common tailはrank==5のROW flex
		Variable leftTail = fresh.getVariable(Variable.Kind.ROW, rank);
		Variable rightTail = fresh.getVariable(Variable.Kind.ROW, rank);
		Variable left = makeRowRoot(fresh, rank,
				Seq.of(
						new RecordField<>("left", boolVar(rank)),
						new RecordField<>("x", i32Var(rank))),
				Optional.of(leftTail));
		Variable right = makeRowRoot(fresh, rank,
				Seq.of(
						new RecordField<>("right", boolVar(rank)),
						new RecordField<>("x", i32Var(rank))),
				Optional.of(rightTail));
		Unify.unify(left, right, fresh, rank);

		VariableState lts = leftTail.get();
		assertTrue(lts.content instanceof Structure);
		Row1 ltr = (Row1) ((Structure) lts.content).flatType();
		assertEquals("right", ltr.fields().at(0).name());
		Variable leftCommonTail = ltr.extension().orElseThrow();

		VariableState rts = rightTail.get();
		assertTrue(rts.content instanceof Structure);
		Row1 rtr = (Row1) ((Structure) rts.content).flatType();
		assertEquals("left", rtr.fields().at(0).name());
		Variable rightCommonTail = rtr.extension().orElseThrow();

		assertTrue(leftCommonTail.isSame(rightCommonTail));
		assertEquals(Variable.Kind.ROW, leftCommonTail.kind());
		assertEquals(rank, leftCommonTail.get().rank);
	}

	@Test
	void closedRowRejectsUnabsorbedResidual() {
		FreshFlex fresh = new FreshFlex();
		int rank = 2;
		// closed left: { x:I32 }
		// closed right: { x:I32, y:Bool }   ← yを吸収するtailがない → Missmatch
		Variable left = closedRowRoot(fresh, rank,
				Seq.of(new RecordField<>("x", i32Var(rank))));
		Variable right = closedRowRoot(fresh, rank, Seq.of(
				new RecordField<>("x", i32Var(rank)),
				new RecordField<>("y", boolVar(rank))));
		assertThrows(Missmatch.class,
				() -> Unify.unify(left, right, fresh, rank));
	}

	@Test
	void commonFieldTypeMismatchIsRejected() {
		FreshFlex fresh = new FreshFlex();
		int rank = 2;
		Variable left = closedRowRoot(fresh, rank,
				Seq.of(new RecordField<>("x", i32Var(rank))));
		Variable right = closedRowRoot(fresh, rank,
				Seq.of(new RecordField<>("x", boolVar(rank))));
		assertThrows(Missmatch.class,
				() -> Unify.unify(left, right, fresh, rank));
	}

	@Test
	void recursiveRowIsRejectedDuringUnification() {
		FreshFlex fresh = new FreshFlex();
		int rank = 2;
		Variable tail = fresh.getVariable(Variable.Kind.ROW, rank);
		Variable row = makeRowRoot(fresh, rank, Seq.of(), Optional.of(tail));
		// fieldがない場合もtailをrow自身へunifyするとrecursive rowになる．
		assertThrows(Missmatch.class,
				() -> Unify.unify(row, tail, fresh, rank));
	}

	@Test
	void knownLabelIsAddedAsLacksConstraintToTail() {
		FreshFlex fresh = new FreshFlex();
		int rank = 2;
		Variable tail = fresh.getVariable(Variable.Kind.ROW, rank);
		Variable row = makeRowRoot(fresh, rank,
				Seq.of(new RecordField<>("x", i32Var(rank))),
				Optional.of(tail));
		// 構築直後，tailのforbidden labelsに "x" が含まれていること
		assertTrue(tail.get().forbiddenLabels().contains("x"),
				"tail must lack 'x' after row construction");
	}

	@Test
	void laterBindingTailToDuplicateLabelIsRejected() {
		FreshFlex fresh = new FreshFlex();
		int rank = 2;
		Variable tail = fresh.getVariable(Variable.Kind.ROW, rank);
		Variable row = makeRowRoot(fresh, rank,
				Seq.of(new RecordField<>("x", i32Var(rank))),
				Optional.of(tail));
		// 後からxを含むRow1をtailへ構築 → lacks違反でMissmatch/IllArg
		Variable dup = makeRowRoot(fresh, rank,
				Seq.of(new RecordField<>("x", boolVar(rank))),
				Optional.empty());
		assertThrows(Missmatch.class,
				() -> Unify.unify(tail, dup, fresh, rank));
	}

	@Test
	void nestedResidualRowsFlattenToCanonicalStableRow() {
		FreshFlex fresh = new FreshFlex();
		int rank = 2;
		Variable leftTail = fresh.getVariable(Variable.Kind.ROW, rank);
		Variable rightTail = fresh.getVariable(Variable.Kind.ROW, rank);
		Variable left = makeRowRoot(fresh, rank,
				Seq.of(new RecordField<>("x", i32Var(rank))),
				Optional.of(leftTail));
		Variable right = makeRowRoot(fresh, rank,
				Seq.of(
						new RecordField<>("x", i32Var(rank)),
						new RecordField<>("y", boolVar(rank))),
				Optional.of(rightTail));
		Unify.unify(left, right, fresh, rank);
		// 共通rootをtoRowで平坦化: { x:I32, y:Bool | rN } のstable single row
		Type.Row flat = left.toRow(ROWNAMER);
		assertEquals(2, flat.fields().size());
		assertEquals("x", flat.fields().at(0).name());
		assertEquals("y", flat.fields().at(1).name());
		assertTrue(flat.extension().isPresent());
	}

	@Test
	void openTailUnifiedWithMatchingClosedRowBecomesClosed() {
		FreshFlex fresh = new FreshFlex();
		int rank = 2;
		Variable openTail = fresh.getVariable(Variable.Kind.ROW, rank);
		Variable openRow = makeRowRoot(fresh, rank,
				Seq.of(new RecordField<>("x", i32Var(rank))),
				Optional.of(openTail));
		// 同一field集合のclosed row
		Variable closed = closedRowRoot(fresh, rank,
				Seq.of(new RecordField<>("x", i32Var(rank))));
		Unify.unify(openRow, closed, fresh, rank);
		// openTailはempty closed Row1へ束縛される
		VariableState tailState = openTail.get();
		assertTrue(tailState.content instanceof Structure,
				"openTail should be bound to closed Row1, got: " + tailState.content);
		Row1 boundTail = (Row1) ((Structure) tailState.content).flatType();
		assertFalse(boundTail.isOpen());
		assertEquals(0, boundTail.fields().size());
	}
}