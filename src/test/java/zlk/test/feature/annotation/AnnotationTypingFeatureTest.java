package zlk.test.feature.annotation;

import org.junit.jupiter.api.Test;

import zlk.util.fixture.CompilationFixture;

public class AnnotationTypingFeatureTest {
	@Test
	void typeAnnotationSpecializesInferredType() {
		var module = CompilationFixture.compileSucceeded(
				"""
				id : I32 -> I32
				id x = x
				""");

		module.assertType("id", "I32 -> I32");
	}

	@Test
	void polymorphicTypeAnnotationCanSpecializeInferredVariables() {
		var module = CompilationFixture.compileSucceeded(
				"""
				const : a -> a -> a
				const x y = x
				""");

		module.assertType("const", "a -> a -> a");
	}

	@Test
	void polymorphicTypeAnnotationIsInstantiatedAtEachUse() {
		var module = CompilationFixture.compileSucceeded(
				"""
				id : a -> a
				id x = x
				int = id 1
				bool = id True
				""");

		module.assertType("id", "a -> a");
		module.assertType("int", "I32");
		module.assertType("bool", "Bool");
	}

	@Test
	void typeAnnotationDescribesTheWholeValueType() {
		var module = CompilationFixture.compileSucceeded(
				"""
				makeAdder : I32 -> I32 -> I32
				makeAdder x = \\y -> add x y
				""");

		module.assertType("makeAdder", "I32 -> I32 -> I32");
	}

	@Test
	void localTypeAnnotationIsEnabled() {
		var module = CompilationFixture.compileSucceeded(
				"""
				use =
				  let
				    id : a -> a
				    id x = x
				    int = id 1
				    bool = id True
				  in
				    int
				""");

		module.assertType("use.id", "a -> a");
		module.assertType("use.int", "I32");
		module.assertType("use.bool", "Bool");
	}

	@Test
	void typeAnnotationSupportsUserDefinedTypes() {
		var module = CompilationFixture.compileSucceeded(
				"""
				type List a =
				  | Nil
				  | Cons a (List a)

				singleton : a -> List a
				singleton x = Cons x Nil
				intList = singleton 1
				boolList = singleton True
				""");

		module.assertType("singleton", "a -> Main.List a");
		module.assertType("intList", "Main.List I32");
		module.assertType("boolList", "Main.List Bool");
	}

	@Test
	void mutuallyRecursiveAnnotationsHaveIndependentTypeVariables() {
		var module = CompilationFixture.compileSucceeded(
				"""
				f : a -> a
				f x = g x

				g : b -> b
				g x = f x
				""");

		module.assertType("f", "a -> a");
		module.assertType("g", "b -> b");
	}

	@Test
	void typeAnnotationBreaksInferenceDependencyCycle() {
		var module = CompilationFixture.compileSucceeded(
				"""
				f : a -> a
				f x = g x

				g x = f x
				""");

		module.assertType("f", "a -> a");
		module.assertType("g", "a -> a");
	}

	@Test
	void nestedTypeAnnotationsShareOuterRigidVariable() {
		var module = CompilationFixture.compileSucceeded(
				"""
				outer : a -> a -> a
				outer x =
				  let
				    inner : a -> a
				    inner y = x
				  in
				    inner
				""");

		module.assertType("outer", "a -> a -> a");
		module.assertType("outer.inner", "a -> a");
	}
}
