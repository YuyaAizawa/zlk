package zlk.feature.record;

import org.junit.jupiter.api.extension.ExtendWith;
import zlk.tester.DumpOnFailureWatcher;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import zlk.common.RecordField;
import zlk.common.Type;
import zlk.runtime.ZlkRecord;
import zlk.tester.ModuleTester;
import zlk.tester.ModuleTester.CompileLevel;
import zlk.tester.ValueTester.VData;
import zlk.util.collection.Seq;

@ExtendWith(DumpOnFailureWatcher.class)
public class RecordFeatureTest {
	@Test
	void emptyRecordLiteral() {
		var module = new ModuleTester("empty = {}", CompileLevel.BYTECODE_GEN);
		Object value = ((VData) module.getValue("empty")).value();

		assertTrue(value instanceof ZlkRecord);
		assertEquals("{}", value.toString());
	}

	@Test
	void recordLiteralUsesCanonicalFieldOrder() {
		var module = new ModuleTester(
				"record = { y = True, x = 1 }",
				CompileLevel.BYTECODE_GEN);
		ZlkRecord value = (ZlkRecord) ((VData) module.getValue("record")).value();

		assertEquals("{ x = 1, y = True }", value.toString());
		assertEquals(1, value.get("x"));
		assertEquals(true, value.get("y"));
	}

	@Test
	void instantiatesNestedRecords() {
		var module = new ModuleTester(
				"nested = { z = { y = True, x = 1 }, a = 2 }",
				CompileLevel.BYTECODE_GEN);
		ZlkRecord outer = (ZlkRecord) ((VData) module.getValue("nested")).value();
		ZlkRecord inner = (ZlkRecord) outer.get("z");

		assertEquals("{ a = 2, z = { x = 1, y = True } }", outer.toString());
		assertEquals(1, inner.get("x"));
		assertEquals(true, inner.get("y"));
	}

	@Test
	void infersNestedRecordsTogetherWithParametricPolymorphism() {
		var module = new ModuleTester(
				"""
				wrap value = { payload = { value = value } }
				wrappedInt = wrap 1
				wrappedBool = wrap True
				""", CompileLevel.BYTECODE_GEN);
		Type.Var a = new Type.Var("a");
		Type.Record genericInner = new Type.Record(Seq.of(new RecordField<>("value", a)));
		Type.Record genericOuter = new Type.Record(Seq.of(new RecordField<>("payload", genericInner)));
		Type.Record intOuter = new Type.Record(Seq.of(new RecordField<>("payload",
				new Type.Record(Seq.of(new RecordField<>("value", Type.I32))))));
		Type.Record boolOuter = new Type.Record(Seq.of(new RecordField<>("payload",
				new Type.Record(Seq.of(new RecordField<>("value", Type.BOOL))))));

		module.getType("wrap").is(new Type.Arrow(a, genericOuter));
		module.getType("wrappedInt").is(intOuter);
		module.getType("wrappedBool").is(boolOuter);
		ZlkRecord intValue = (ZlkRecord) ((VData) module.getValue("wrappedInt")).value();
		ZlkRecord boolValue = (ZlkRecord) ((VData) module.getValue("wrappedBool")).value();
		assertEquals(1, ((ZlkRecord) intValue.get("payload")).get("value"));
		assertEquals(true, ((ZlkRecord) boolValue.get("payload")).get("value"));
	}

	@Test
	void accessesNestedFieldsAndUpdatesImmutably() {
		var module = new ModuleTester(
				"""
				base = { y = True, x = 1 }
				updated = { base | x = 2 }
				nested = { outer = updated }
				selected = nested.outer.x
				baseX = base.x
				""", CompileLevel.BYTECODE_GEN);

		module.getType("selected").is(Type.I32);
		assertEquals(2, ((VData) module.getValue("selected")).value());
		assertEquals(1, ((VData) module.getValue("baseX")).value());
	}

	@Test
	void updatesMultipleRecordFieldsImmutably() {
		var module = new ModuleTester(
				"""
				base = { z = 3, y = True, x = 1 }
				updated = { base | x = 2, y = False }
				baseX = base.x
				baseY = base.y
				updatedX = updated.x
				updatedY = updated.y
				updatedZ = updated.z
				""", CompileLevel.BYTECODE_GEN);

		assertEquals(1, ((VData) module.getValue("baseX")).value());
		assertEquals(true, ((VData) module.getValue("baseY")).value());
		assertEquals(2, ((VData) module.getValue("updatedX")).value());
		assertEquals(false, ((VData) module.getValue("updatedY")).value());
		assertEquals(3, ((VData) module.getValue("updatedZ")).value());
	}

	@Test
	void acceptsNestedRecordTypeAnnotations() {
		var module = new ModuleTester(
				"""
				getValue : { outer : { value : I32 } } -> I32
				getValue record = record.outer.value
				result = getValue { outer = { value = 7 } }
				""", CompileLevel.BYTECODE_GEN);

		Type.Record argument = new Type.Record(Seq.of(new RecordField<>("outer",
				new Type.Record(Seq.of(new RecordField<>("value", Type.I32))))));
		module.getType("getValue").is(new Type.Arrow(argument, Type.I32));
		assertEquals(7, ((VData) module.getValue("result")).value());
	}

	@Test
	void distinguishesEmptyRecordTypeFromUnit() {
		var module = new ModuleTester(
				"""
				empty : {}
				empty = {}
				""", CompileLevel.BYTECODE_GEN);

		module.getType("empty").is(new Type.Record(Seq.of()));
		assertEquals("{}", ((VData) module.getValue("empty")).value().toString());
	}

	@Test
	void inferredAccessorAcceptsWiderShapesAtBytecodeLevel() {
		String src =
				"""
				getX record = record.x
				intResult = getX { x = 1, y = True }
				nestedResult = getX { x = 2, z = 3, w = False }
				""";

		var module = new ModuleTester(src, CompileLevel.BYTECODE_GEN);

		// inferred accessorが異なるwider shapesでBYTECODE_GEN実行し値をassert
		assertEquals(1, ((VData) module.getValue("intResult")).value());
		assertEquals(2, ((VData) module.getValue("nestedResult")).value());
	}

	@Test
	void polymorphicUpdatePreservesExtraFieldsInZlkRecord() {
		String src =
				"""
				setX record value = { record | x = value }
				base = { y = True, x = 1 }
				updated = setX base 2
				updatedX = updated.x
				updatedY = updated.y
				baseX = base.x
				""";

		var module = new ModuleTester(src, CompileLevel.BYTECODE_GEN);

		ZlkRecord updatedValue = (ZlkRecord) ((VData) module.getValue("updated")).value();
		assertEquals(2, updatedValue.get("x"));
		assertEquals(true, updatedValue.get("y"));
		assertEquals("{ x = 2, y = True }", updatedValue.toString());
		assertEquals(2, ((VData) module.getValue("updatedX")).value());
		assertEquals(true, ((VData) module.getValue("updatedY")).value());
		assertEquals(1, ((VData) module.getValue("baseX")).value());
	}

	@Test
	void annotatedRowPolymorphicAccessorRunsAtBytecodeLevel() {
		String src =
				"""
				getX : { r | x : I32 } -> I32
				getX record = record.x
				intResult = getX { x = 1, y = True }
				nestedResult = getX { x = 42, z = 3, w = False }
				""";

		var module = new ModuleTester(src, CompileLevel.BYTECODE_GEN);

		assertEquals(1, ((VData) module.getValue("intResult")).value());
		assertEquals(42, ((VData) module.getValue("nestedResult")).value());
	}
}
