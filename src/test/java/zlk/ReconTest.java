package zlk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import zlk.common.RecordField;
import zlk.common.Type;
import zlk.idcalc.IcCaseBranch;
import zlk.idcalc.IcExp.IcApp;
import zlk.idcalc.IcExp.IcCase;
import zlk.idcalc.IcExp.IcCnst;
import zlk.idcalc.IcExp.IcVarLocal;
import zlk.idcalc.IcPattern;
import zlk.tester.ModuleTester;
import zlk.tester.ModuleTester.CompileLevel;
import zlk.util.collection.Seq;

public class ReconTest {
	@Test
	void rowAndRecordAreDistinctClasses() {
		Type.Row closedRow = new Type.Row(
				Seq.of(new RecordField<>("x", Type.I32)),
				Optional.empty());
		Type.Record record = new Type.Record(closedRow);

		assertInstanceOf(Type.Row.class,    closedRow);
		assertInstanceOf(Type.Record.class, record);
		assertFalse(Type.Record.class.isInstance(closedRow),
				"Row は Record とは別クラスであるべき");
		assertFalse(Type.Row.class.isInstance(record),
				"Record は Row とは別クラスであるべき");
	}

	@Test
	void rowVarIsNotAType() {
		Type.RowVar rowVar = new Type.RowVar("row");

		assertFalse(Type.class.isInstance(rowVar),
				"RowVar は Type を実装してはならない");
	}

	@Test
	void closedRowPrettyPrintIsCanonical() {
		Type.Row closedRow = new Type.Row(
				Seq.of(
					new RecordField<>("y", Type.I32),
					new RecordField<>("x", Type.BOOL)),
				Optional.empty());

		assertEquals("{ x : Bool, y : I32 }", Type.buildRowString(closedRow));
	}

	@Test
	void openRowPrettyPrintPlacesRowVarBeforeFields() {
		Type.RowVar rowVar = new Type.RowVar("row");
		Type.Record openRecord = new Type.Record(
				Seq.of(new RecordField<>("x", Type.I32)),
				Optional.of(rowVar));
		Type.Row openRow = openRecord.row();

		assertEquals("{ row | x : I32 }", Type.buildRowString(openRow));
	}

	@Test
	void recordTypeRejectsDuplicateLabelsEvenWhenFieldTypesDiffer() {
		assertThrows(IllegalArgumentException.class, () -> new Type.Record(
				Seq.of(
					new RecordField<>("x", Type.I32),
					new RecordField<>("x", Type.BOOL))));
	}

	@Test
	void fieldAccessInfersOpenSingleFieldRecord() {
		var module = new ModuleTester(
				"getX record = record.x",
				CompileLevel.TYPE_RECON);

		// field accessはopen rowへ推論される: { r | x : a } -> a
		Type.Var a = new Type.Var("a");
		Type.RowVar r = new Type.RowVar("b");
		module.getType("getX").is(new Type.Arrow(
				new Type.Record(
						Seq.of(new RecordField<>("x", a)),
						Optional.of(r)),
				a));
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
	void inferredFieldAccessorAcceptsWiderRecordWithRowPolymorphism() {
		String src = """
				getX record = record.x
				result = getX { x = 1, y = True }
				""";

		var module = new ModuleTester(src, CompileLevel.TYPE_RECON);
		module.getType("result").is("I32");
	}

	@Test
	void rowPolymorphicAccessorIsInstantiatedAtEachUse() {
		var module = new ModuleTester("""
				getX record = record.x
				int = getX { x = 1, y = True }
				bool = getX { x = False, z = 2 }
				""", CompileLevel.TYPE_RECON);

		module.getType("int").is(Type.I32);
		module.getType("bool").is(Type.BOOL);
	}

	@Test
	void multipleFieldAccessesAccumulateInOneOpenRow() {
		var module = new ModuleTester("""
				sum record = add record.x record.y
				result = sum { x = 1, y = 2, tag = True }
				""", CompileLevel.TYPE_RECON);
		Type.Record argument = new Type.Record(
				Seq.of(
						new RecordField<>("x", Type.I32),
						new RecordField<>("y", Type.I32)),
				Optional.of(new Type.RowVar("a")));

		module.getType("sum").is(new Type.Arrow(argument, Type.I32));
		module.getType("result").is(Type.I32);
	}

	@Test
	void recordUpdatePreservesOpenShapeAndFieldType() {
		var module = new ModuleTester("""
				setX record value = { record | x = value }
				updated = setX { x = 1, y = True } 2
				""", CompileLevel.TYPE_RECON);
		Type.Var field = new Type.Var("a");
		Type.Record open = new Type.Record(
				Seq.of(new RecordField<>("x", field)),
				Optional.of(new Type.RowVar("b")));
		Type.Record updated = new Type.Record(Seq.of(
				new RecordField<>("x", Type.I32),
				new RecordField<>("y", Type.BOOL)));

		module.getType("setX").is(new Type.Arrow(
				open, new Type.Arrow(field, open)));
		module.getType("updated").is(updated);
	}

	@Test
	void recordUpdateRejectsClosedRecordWithoutUpdatedField() {
		assertThrows(RuntimeException.class, () -> new ModuleTester("""
				setX record value = { record | x = value }
				bad = setX { y = True } 1
				""", CompileLevel.TYPE_RECON));
	}

	@Test
	void openRowAnnotationCanDescribePolymorphicRecordUpdate() {
		var module = new ModuleTester("""
				setX : { r | x : a } -> a -> { r | x : a }
				setX record value = { record | x = value }
				updated = setX { x = 1, y = True } 2
				""", CompileLevel.TYPE_RECON);
		Type.Var field = new Type.Var("a");
		Type.Record open = new Type.Record(
				Seq.of(new RecordField<>("x", field)),
				Optional.of(new Type.RowVar("r")));

		module.getType("setX").is(new Type.Arrow(
				open, new Type.Arrow(field, open)));
		module.getType("updated").is(new Type.Record(Seq.of(
				new RecordField<>("x", Type.I32),
				new RecordField<>("y", Type.BOOL))));
	}

	@Test
	void partialRecordPatternIsRowPolymorphic() {
		var module = new ModuleTester("""
				pick { x = x } = x
				int = pick { x = 1, y = True }
				bool = pick { x = False, z = 2 }
				""", CompileLevel.TYPE_RECON);

		module.getType("int").is(Type.I32);
		module.getType("bool").is(Type.BOOL);
	}

	@Test
	void emptyRecordPatternInfersAnOpenRecord() {
		var module = new ModuleTester("""
				ignore {} = 1
				result = ignore { x = True }
				""", CompileLevel.TYPE_RECON);
		Type.Record anyRecord = new Type.Record(
				Seq.of(), Optional.of(new Type.RowVar("a")));

		module.getType("ignore").is(new Type.Arrow(anyRecord, Type.I32));
		module.getType("result").is(Type.I32);
	}

	@Test
	void fieldAccessRejectsClosedRecordWithoutRequestedField() {
		assertThrows(RuntimeException.class, () -> new ModuleTester("""
				getX record = record.x
				bad = getX { y = True }
				""", CompileLevel.TYPE_RECON));
	}

	@Test
	void openRowAnnotationAllowsFieldAccessFromWiderRecord() {
		String src = """
				getX : { r | x : I32 } -> I32
				getX record = record.x
				result = getX { x = 1, y = True }
				""";
		var module = new ModuleTester(src, CompileLevel.TYPE_RECON);
		module.getType("getX").is(new Type.Arrow(
				new Type.Record(
						Seq.of(new RecordField<>("x", Type.I32)),
						Optional.of(new Type.RowVar("r"))),
				Type.I32));
		module.getType("result").is(Type.I32);
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

	// ===== 複数ROW kind parameterを持つ透明aliasの受け入れテスト群 =====
	// 関数型alias Mapper inputRow outputRow a b は，inputRowとoutputRowが別々のROW parameter，
	// aとbがTYPE parameterである．alias本体は
	//   { inputRow | value : a } -> { outputRow | value : b }
	// に展開される．

	@Test
	void functionAliasWithMultipleRowParametersAcceptsSameRowForInputAndOutput() {
		// 同じrow変数 r をinputRow/outputRowの両方に渡す．
		// sameRow : Mapper r r I32 I32 は { r | value : I32 } -> { r | value : I32 } に展開される．
		// 本体 { record | value = 2 } は同じopen rowを保つので注釈を満たす．
		String src = """
				type alias Mapper inputRow outputRow a b = { inputRow | value : a } -> { outputRow | value : b }
				sameRow : Mapper r r I32 I32
				sameRow record = { record | value = 2 }
				sameResult = sameRow { x = 1, value = 5 }
				""";
		var module = new ModuleTester(src, CompileLevel.TYPE_RECON);

		Type.RowVar r = new Type.RowVar("r");
		Type.Record openArg = new Type.Record(
				Seq.of(new RecordField<>("value", Type.I32)),
				Optional.of(r));
		// 同じrow変数 r を使うため，結果側のopen rowも同じ RowVar インスタンスで比較する．
		Type.Record openRet = new Type.Record(
				Seq.of(new RecordField<>("value", Type.I32)),
				Optional.of(r));
		// TypeTester.is(Type) は構造等価判定なので，変数名を注釈と同じ r に揃える．
		module.getType("sameRow").is(new Type.Arrow(openArg, openRet));

		// 結果値の型: { x : I32, value : I32 } のclosed record．
		module.getType("sameResult").is(new Type.Record(Seq.of(
				new RecordField<>("value", Type.I32),
				new RecordField<>("x", Type.I32))));
	}

	@Test
	void functionAliasWithMultipleRowParametersAcceptsDistinctRowsForInputAndOutput() {
		// 異なるrowを実引数に渡す．inputRow は { x : I32 }，outputRow は { y : Bool }．
		// Mapper { x : I32 } { y : Bool } I32 Bool は
		//   { x : I32, value : I32 } -> { y : Bool, value : Bool }
		// に展開される．本体は入力 record を使わず，出力行の閉じたrecordを直接構築する．
		// ROW kind parameter 2個が独立して展開されることを観測する．
		String src = """
				type alias Mapper inputRow outputRow a b = { inputRow | value : a } -> { outputRow | value : b }
				diffRow : Mapper { x : I32 } { y : Bool } I32 Bool
				diffRow record = { y = True, value = True }
				diffResult = diffRow { x = 1, value = 5 }
				""";
		var module = new ModuleTester(src, CompileLevel.TYPE_RECON);

		// diffRow は { x : I32, value : I32 } -> { y : Bool, value : Bool }
		Type.Record openArg = new Type.Record(Seq.of(
				new RecordField<>("value", Type.I32),
				new RecordField<>("x", Type.I32)));
		Type.Record openRet = new Type.Record(Seq.of(
				new RecordField<>("value", Type.BOOL),
				new RecordField<>("y", Type.BOOL)));
		module.getType("diffRow").is(new Type.Arrow(openArg, openRet));

		// 結果値の型: { y : Bool, value : Bool } のclosed record．
		module.getType("diffResult").is(new Type.Record(Seq.of(
				new RecordField<>("value", Type.BOOL),
				new RecordField<>("y", Type.BOOL))));
	}
}
