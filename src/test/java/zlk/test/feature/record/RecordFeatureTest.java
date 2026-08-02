package zlk.test.feature.record;

import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import zlk.runtime.ZlkRecord;
import zlk.util.fixture.CompilationFixture;
import zlk.util.tester.DumpOnFailureWatcher;

@ExtendWith(DumpOnFailureWatcher.class)
public class RecordFeatureTest {
	@Test
	void emptyRecordLiteral() {
		var module = CompilationFixture.compileSucceeded("empty = {}");
		Object value = module.value("empty");

		assertTrue(value instanceof ZlkRecord);
		assertEquals("{}", value.toString());
	}

	@Test
	void recordLiteralUsesCanonicalFieldOrder() {
		var module = CompilationFixture.compileSucceeded("record = { y = True, x = 1 }");
		ZlkRecord value = (ZlkRecord) module.value("record");

		assertEquals("{ x = 1, y = True }", value.toString());
		assertEquals(1, value.get("x"));
		assertEquals(true, value.get("y"));
	}

	@Test
	void instantiatesNestedRecords() {
		var module = CompilationFixture.compileSucceeded(
				"nested = { z = { y = True, x = 1 }, a = 2 }");
		ZlkRecord outer = (ZlkRecord) module.value("nested");
		ZlkRecord inner = (ZlkRecord) outer.get("z");

		assertEquals("{ a = 2, z = { x = 1, y = True } }", outer.toString());
		assertEquals(1, inner.get("x"));
		assertEquals(true, inner.get("y"));
	}

	@Test
	void infersNestedRecordsTogetherWithParametricPolymorphism() {
		var module = CompilationFixture.compileSucceeded(
				"""
				wrap value = { payload = { value = value } }
				wrappedInt = wrap 1
				wrappedBool = wrap True
				""");

		module.assertType("wrap", "a -> { payload : { value : a } }");
		module.assertType("wrappedInt", "{ payload : { value : I32 } }");
		module.assertType("wrappedBool", "{ payload : { value : Bool } }");
		ZlkRecord intValue = (ZlkRecord) module.value("wrappedInt");
		ZlkRecord boolValue = (ZlkRecord) module.value("wrappedBool");
		assertEquals(1, ((ZlkRecord) intValue.get("payload")).get("value"));
		assertEquals(true, ((ZlkRecord) boolValue.get("payload")).get("value"));
	}

	@Test
	void accessesNestedFieldsAndUpdatesImmutably() {
		var module = CompilationFixture.compileSucceeded(
				"""
				base = { y = True, x = 1 }
				updated = { base | x = 2 }
				nested = { outer = updated }
				selected = nested.outer.x
				baseX = base.x
				""");

		module.assertType("selected", "I32");
		int selected = (int) module.value("selected");
		int baseX = (int) module.value("baseX");
		assertEquals(2, selected);
		assertEquals(1, baseX);
	}

	@Test
	void updatesMultipleRecordFieldsImmutably() {
		var module = CompilationFixture.compileSucceeded(
				"""
				base = { z = 3, y = True, x = 1 }
				updated = { base | x = 2, y = False }
				baseX = base.x
				baseY = base.y
				updatedX = updated.x
				updatedY = updated.y
				updatedZ = updated.z
				""");

		int baseX = (int) module.value("baseX");
		boolean baseY = (boolean) module.value("baseY");
		int updatedX = (int) module.value("updatedX");
		boolean updatedY = (boolean) module.value("updatedY");
		int updatedZ = (int) module.value("updatedZ");
		assertEquals(1, baseX);
		assertEquals(true, baseY);
		assertEquals(2, updatedX);
		assertEquals(false, updatedY);
		assertEquals(3, updatedZ);
	}

	@Test
	void acceptsNestedRecordTypeAnnotations() {
		var module = CompilationFixture.compileSucceeded(
				"""
				getValue : { outer : { value : I32 } } -> I32
				getValue record = record.outer.value
				result = getValue { outer = { value = 7 } }
				""");

		module.assertType("getValue", "{ outer : { value : I32 } } -> I32");
		int actual = (int) module.value("result");
		assertEquals(7, actual);
	}

	@Test
	void distinguishesEmptyRecordTypeFromUnit() {
		var module = CompilationFixture.compileSucceeded(
				"""
				empty : {}
				empty = {}
				""");

		module.assertType("empty", "{  }");
		ZlkRecord actual = (ZlkRecord) module.value("empty");
		assertEquals("{}", actual.toString());
	}

	@Test
	void inferredAccessorAcceptsWiderShapesAtBytecodeLevel() {
		String src =
				"""
				getX record = record.x
				intResult = getX { x = 1, y = True }
				nestedResult = getX { x = 2, z = 3, w = False }
				""";

		var module = CompilationFixture.compileSucceeded(src);

		// inferred accessorが異なるwider shapesでBYTECODE_GEN実行し値をassert
		int intResult = (int) module.value("intResult");
		int nestedResult = (int) module.value("nestedResult");
		assertEquals(1, intResult);
		assertEquals(2, nestedResult);
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

		var module = CompilationFixture.compileSucceeded(src);

		ZlkRecord updatedValue = (ZlkRecord) module.value("updated");
		assertEquals(2, updatedValue.get("x"));
		assertEquals(true, updatedValue.get("y"));
		assertEquals("{ x = 2, y = True }", updatedValue.toString());
		int updatedX = (int) module.value("updatedX");
		boolean updatedY = (boolean) module.value("updatedY");
		int baseX = (int) module.value("baseX");
		assertEquals(2, updatedX);
		assertEquals(true, updatedY);
		assertEquals(1, baseX);
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

		var module = CompilationFixture.compileSucceeded(src);

		int intResult = (int) module.value("intResult");
		int nestedResult = (int) module.value("nestedResult");
		assertEquals(1, intResult);
		assertEquals(42, nestedResult);
	}
}
