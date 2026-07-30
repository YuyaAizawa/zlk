package zlk.phase.recon;

import org.junit.jupiter.api.extension.ExtendWith;
import zlk.tester.DumpOnFailureWatcher;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import zlk.common.RecordField;
import zlk.common.Type;
import zlk.common.id.Id;
import zlk.tester.ModuleTester;
import zlk.tester.ModuleTester.CompileLevel;
import zlk.util.collection.Seq;

@ExtendWith(DumpOnFailureWatcher.class)
public class TypeAliasReconstructionTest {
	@Test
	void aliasExpandsEmptyRecordArgumentToClosedRow() {
		// Foo a = { a | x : I32, y : Bool }
		// Foo {} -> { x : I32, y : Bool } (tail close)
		String src = """
				type alias Foo a = { a | x : I32, y : Bool }
				closed : Foo {}
				closed = { x = 1, y = True }
				""";
		var module = new ModuleTester(src, CompileLevel.TYPE_RECON);

		Type.Record expected = new Type.Record(Seq.of(
				new RecordField<>("x", Type.I32),
				new RecordField<>("y", Type.BOOL)));
		module.getType("closed").is(expected);
	}

	@Test
	void aliasExpandsWiderClosedRecordArgument() {
		// Foo {} -> {x,y}; Foo { z : I32 } -> {x,y,z}
		String src = """
				type alias Foo a = { a | x : I32, y : Bool }
				wider : Foo { z : I32 }
				wider = { x = 1, y = True, z = 2 }
				""";
		var module = new ModuleTester(src, CompileLevel.TYPE_RECON);

		Type.Record expected = new Type.Record(Seq.of(
				new RecordField<>("x", Type.I32),
				new RecordField<>("y", Type.BOOL),
				new RecordField<>("z", Type.I32)));
		module.getType("wider").is(expected);
	}

	@Test
	void plainTypeAliasExpandsInValueAnnotation() {
		// type alias Box a = { value : a } (TYPE alias)
		String src = """
				type alias Box a = { value : a }
				box : Box I32
				box = { value = 1 }
				""";
		var module = new ModuleTester(src, CompileLevel.TYPE_RECON);

		Type.Record expected = new Type.Record(Seq.of(
				new RecordField<>("value", Type.I32)));
		module.getType("box").is(expected);
	}

	@Test
	void aliasAllowsForwardReference() {
		String src = """
				type alias B = A I32
				type A a = A0 a
				zero : B
				zero = A0 0
				""";
		var module = new ModuleTester(src, CompileLevel.TYPE_RECON);
		module.getType("zero").is(new Type.CtorApp(Id.intern("Main.A"), Seq.of(Type.I32)));
	}

	@Test
	void aliasAllowsForwardReferenceToAnotherAlias() {
		var module = new ModuleTester("""
				type alias B a = A a
				type alias A a = { value : a }
				box : B I32
				box = { value = 1 }
				""", CompileLevel.TYPE_RECON);
		module.getType("box").is(new Type.Record(
				Seq.of(new RecordField<>("value", Type.I32))));
	}

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
