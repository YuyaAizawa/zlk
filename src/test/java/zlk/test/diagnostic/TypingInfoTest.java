package zlk.test.diagnostic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import zlk.compiler.CompilationOptions;
import zlk.compiler.CompilationOptions.Key;
import zlk.compiler.diagnostic.Diagnostic;
import zlk.compiler.driver.Driver;
import zlk.compiler.id.Id;
import zlk.compiler.ir.typing.Type;
import zlk.util.collection.Seq;
import zlk.util.fixture.CompilationFixture;

public class TypingInfoTest {
	@Test
	void existingCompileOverloadAndDisabledOptionDoNotReportInferredTypes() {
		String src = "module Main\nanswer = 42\n";
		Driver.CompilationResult.Succeeded defaultResult = succeeded(Driver.compile("Main.zlk", src));
		Driver.CompilationResult.Succeeded disabledResult = succeeded(
				Driver.compile(
						"Main.zlk",
						src,
						CompilationOptions.DEFAULT.disable(Key.REPORT_INFERRED_TYPES)));

		assertEquals(0, defaultResult.diags().size());
		assertEquals(0, disabledResult.diags().size());
	}

	@Test
	void enabledOptionReportsTheDeclarationIdLocationAndTypeWithoutBlockingCompilation() {
		CompilationFixture module = CompilationFixture.compile("answer = 42\n");
		Driver.CompilationResult.Succeeded succeeded = succeeded(module.result());

		assertEquals(1, succeeded.diags().size());
		Diagnostic.InferredType inferred = module.inferredTypes().head();
		assertEquals(Diagnostic.Severity.INFO, inferred.severity());
		assertEquals(2, inferred.location().startLine());
		assertEquals(Id.intern("Main.answer"), inferred.declaration());
		assertEquals(Type.I32, inferred.type());
	}

	@Test
	void declarationsAreReportedInDeterministicLexicalPreorder() {
		CompilationFixture module = CompilationFixture.compile(
				"""
				outer =
				  let
				    local =
				      let
				        nested = 1
				      in
				        nested
				    sibling = True
				  in
				    local
				second = 2
				""");
		succeeded(module.result());

		assertEquals(
				"Main.outer,Main.outer.local,Main.outer.sibling,Main.outer.local.nested,Main.second",
				module.inferredTypes().map(info -> info.declaration().canonicalName()).join(","));
	}

	@Test
	void adtConstructorsAreReportedButBuiltinsLambdaArgumentsAndPatternBindersAreNot() {
		CompilationFixture module = CompilationFixture.compile(
				"""
				type Maybe a =
				  | Nothing
				  | Just a
				value input =
				  case input of
				    Nothing -> \\lambdaArg -> add lambdaArg 1
				    Just patternBinder ->
				      let
				        local = \\lambdaArg -> add patternBinder lambdaArg
				      in
				        local
				""");
		succeeded(module.result());

		Seq<Diagnostic.InferredType> inferred = module.inferredTypes();
		assertEquals(
				"Main.Maybe.Nothing,Main.Maybe.Just,Main.value,Main.value._case1_2.local",
				inferred.map(info -> info.declaration().canonicalName()).join(","));

		Type.Var a = new Type.Var("a");
		Type maybeA = new Type.CtorApp(Id.intern("Main.Maybe"), Seq.of(a));
		assertEquals(maybeA, inferred.at(0).type());
		assertEquals(new Type.Arrow(a, maybeA), inferred.at(1).type());
		assertEquals(3, inferred.at(0).location().startLine());
		assertEquals(4, inferred.at(1).location().startLine());
	}

	@Test
	void reconstructionFailureIsReturnedByTheCommonDriverPath() {
		CompilationFixture module = CompilationFixture.compile("bad = add True 1\n");
		assertInstanceOf(
				Driver.CompilationResult.Failed.class,
				module.result());

		assertEquals(1, module.diagnostics(Diagnostic.TypeMismatch.class).size());
	}

	@Test
	void patternErrorFollowsInferredTypeInfoAndCompilationFails() {
		CompilationFixture module = CompilationFixture.compile(
				"""
				toInt b =
				  case b of
				    True -> 1
				""");
		assertInstanceOf(
				Driver.CompilationResult.Failed.class,
				module.result());

		assertEquals(1, module.inferredTypes().size());
		assertEquals(Id.intern("Main.toInt"), module.inferredTypes().head().declaration());
		assertEquals(1, module.diagnostics(Diagnostic.IncompletePattern.class).size());
		assertTrue(indexOf(module.diagnostics(), Diagnostic.InferredType.class)
				< indexOf(module.diagnostics(), Diagnostic.IncompletePattern.class));
		assertTrue(module.diagnostics(Diagnostic.IncompletePattern.class).head().isError());
	}

	private static int indexOf(Seq<Diagnostic> diagnostics, Class<? extends Diagnostic> type) {
		for(int i = 0; i < diagnostics.size(); i++) {
			if(type.isInstance(diagnostics.at(i))) {
				return i;
			}
		}
		return -1;
	}

	private static Driver.CompilationResult.Succeeded succeeded(Driver.CompilationResult result) {
		return assertInstanceOf(Driver.CompilationResult.Succeeded.class, result);
	}
}
