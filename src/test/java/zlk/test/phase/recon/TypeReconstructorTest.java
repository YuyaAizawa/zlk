package zlk.test.phase.recon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;

import zlk.compiler.diagnostic.Diagnostic;
import zlk.compiler.id.Id;
import zlk.compiler.phase.recon.ConstraintExtractor;
import zlk.compiler.phase.recon.FreshFlex;
import zlk.compiler.phase.recon.TypeReconstructor;
import zlk.util.collection.SeqBuffer;
import zlk.util.tester.ModuleTester;
import zlk.util.tester.ModuleTester.CompileLevel;

public class TypeReconstructorTest {
	@Test
	void annotatedRecordFunctionPreservesSharedTypeVariable() {
		var reconstructed = reconstruct("""
				make : a -> a -> { first : a, second : a }
				make first second = { first = first, second = second }
				integer = make 10 20
				boolean = make True False
				""");
		assertEquals("a -> a -> { first : a, second : a }",
				reconstructed.types().get(Id.intern("Main.make")).buildString());
		assertEquals("{ first : I32, second : I32 }",
				reconstructed.types().get(Id.intern("Main.integer")).buildString());
		assertEquals("{ first : Bool, second : Bool }",
				reconstructed.types().get(Id.intern("Main.boolean")).buildString());
	}

	@Test
	void polymorphicFunctionFieldPreservesSharedArgumentAndResultVariable() {
		var reconstructed = reconstruct("""
				hold : (a -> a) -> { transform : a -> a }
				hold transform = { transform = transform }
				identity x = x
				integer = (hold identity).transform 10
				boolean = (hold identity).transform True
				""");
		assertEquals("(a -> a) -> { transform : a -> a }",
				reconstructed.types().get(Id.intern("Main.hold")).buildString());
		assertEquals("I32", reconstructed.types().get(Id.intern("Main.integer")).buildString());
		assertEquals("Bool", reconstructed.types().get(Id.intern("Main.boolean")).buildString());
	}

	private static TypeReconstructor.Result reconstruct(String src) {
		var module = new ModuleTester(src, CompileLevel.NAME_EVAL).getIdcalcModule();
		FreshFlex fresh = new FreshFlex();
		var extracted = ConstraintExtractor.extract(module, fresh);
		SeqBuffer<Diagnostic> diagnostics = new SeqBuffer<>();
		return TypeReconstructor.recon(extracted, fresh, diagnostics::add).fold(
				result -> result,
				() -> fail("type reconstruction failed: " + diagnostics.toSeq()));
	}
}
