package zlk.phase.recon;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import zlk.common.RecordField;
import zlk.common.Type;
import zlk.tester.ModuleTester;
import zlk.tester.ModuleTester.CompileLevel;
import zlk.util.collection.Seq;

public class RecordInferenceTest {
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
}
