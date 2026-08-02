package zlk.test.feature.record;

import org.junit.jupiter.api.Test;

import zlk.util.fixture.CompilationFixture;

public class RecordTypingFeatureTest {
	@Test
	void fieldAccessInfersOpenSingleFieldRecord() {
		var module = CompilationFixture.compileSucceeded("getX record = record.x");

		module.assertType("getX", "{ b | x : a } -> a");
	}

	@Test
	void completeRecordAnnotationAllowsAccessToMultipleFields() {
		var module = CompilationFixture.compileSucceeded(
				"""
				sum : { x : I32, y : I32 } -> I32
				sum record = add record.x record.y
				""");

		module.assertType("sum", "{ x : I32, y : I32 } -> I32");
	}

	@Test
	void inferredFieldAccessorAcceptsWiderRecordWithRowPolymorphism() {
		var module = CompilationFixture.compileSucceeded(
				"""
				getX record = record.x
				result = getX { x = 1, y = True }
				""");

		module.assertType("result", "I32");
	}

	@Test
	void rowPolymorphicAccessorIsInstantiatedAtEachUse() {
		var module = CompilationFixture.compileSucceeded(
				"""
				getX record = record.x
				int = getX { x = 1, y = True }
				bool = getX { x = False, z = 2 }
				""");

		module.assertType("int", "I32");
		module.assertType("bool", "Bool");
	}

	@Test
	void multipleFieldAccessesAccumulateInOneOpenRow() {
		var module = CompilationFixture.compileSucceeded(
				"""
				sum record = add record.x record.y
				result = sum { x = 1, y = 2, tag = True }
				""");

		module.assertType("sum", "{ a | x : I32, y : I32 } -> I32");
		module.assertType("result", "I32");
	}

	@Test
	void recordUpdatePreservesOpenShapeAndFieldType() {
		var module = CompilationFixture.compileSucceeded(
				"""
				setX record value = { record | x = value }
				updated = setX { x = 1, y = True } 2
				""");

		module.assertType("setX", "{ b | x : a } -> a -> { b | x : a }");
		module.assertType("updated", "{ x : I32, y : Bool }");
	}

	@Test
	void openRowAnnotationCanDescribePolymorphicRecordUpdate() {
		var module = CompilationFixture.compileSucceeded(
				"""
				setX : { r | x : a } -> a -> { r | x : a }
				setX record value = { record | x = value }
				updated = setX { x = 1, y = True } 2
				""");

		module.assertType("setX", "{ r | x : a } -> a -> { r | x : a }");
		module.assertType("updated", "{ x : I32, y : Bool }");
	}

	@Test
	void openRowAnnotationAllowsFieldAccessFromWiderRecord() {
		var module = CompilationFixture.compileSucceeded(
				"""
				getX : { r | x : I32 } -> I32
				getX record = record.x
				result = getX { x = 1, y = True }
				""");

		module.assertType("getX", "{ r | x : I32 } -> I32");
		module.assertType("result", "I32");
	}
}
