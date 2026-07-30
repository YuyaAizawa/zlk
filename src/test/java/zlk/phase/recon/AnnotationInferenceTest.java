package zlk.phase.recon;

import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import zlk.tester.ModuleTester;
import zlk.tester.ModuleTester.CompileLevel;

public class AnnotationInferenceTest {
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
}
