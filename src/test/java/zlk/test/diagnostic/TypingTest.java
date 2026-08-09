package zlk.test.diagnostic;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import zlk.compiler.Driver;
import zlk.diagnostic.Diagnostic;
import zlk.util.fixture.CompilationFixture;

public class TypingTest {
	@Test
	void ordinaryTypeMismatchIsReportedAtTheMismatchingExpression() {
		Diagnostic.TypeMismatch error = only(Diagnostic.TypeMismatch.class,
				"""
				bad = add True 1
				""");

		assertEquals(2, error.location().startLine());
		assertInstanceOf(Diagnostic.TypingContext.CallArgument.class, error.context());
	}

	@Test
	void functionArgumentMismatchReportsArgumentContext() {
		Diagnostic.TypeMismatch error = only(Diagnostic.TypeMismatch.class,
				"""
				id : I32 -> I32
				id x = x
				bad = id True
				""");

		Diagnostic.TypingContext.CallArgument context = assertInstanceOf(
				Diagnostic.TypingContext.CallArgument.class, error.context());
		assertEquals("id", context.function());
		assertEquals(0, context.index());
		assertEquals(4, error.location().startLine());
	}

	@Test
	void ifConditionMismatchReportsIfConditionContext() {
		Diagnostic.TypeMismatch error = only(Diagnostic.TypeMismatch.class,
				"""
				bad = if 1 then
				  2
				else
				  3
				""");

		assertInstanceOf(Diagnostic.TypingContext.IfCondition.class, error.context());
		assertEquals(2, error.location().startLine());
	}

	@Test
	void ifBranchMismatchReportsTheMismatchingBranch() {
		Diagnostic.TypeMismatch error = only(Diagnostic.TypeMismatch.class,
				"""
				bad = if True then
				  1
				else
				  False
				""");

		assertInstanceOf(Diagnostic.TypingContext.None.class, error.context());
		assertEquals(5, error.location().startLine());
	}

	@Test
	void infiniteTypeReportsTheDeclarationLocationAndName() {
		Diagnostic.InfiniteType error = only(Diagnostic.InfiniteType.class,
				"""
				f x = x x
				""");

		assertEquals("f", error.declaration());
		assertEquals(2, error.location().startLine());
	}

	@Test
	void constructorArgumentMismatchReportsArgumentContext() {
		Diagnostic.TypeMismatch error = only(Diagnostic.TypeMismatch.class,
				"""
				type Box = Box I32
				bad = Box True
				""");

		Diagnostic.TypingContext.CallArgument context = assertInstanceOf(
				Diagnostic.TypingContext.CallArgument.class, error.context());
		assertEquals("Box", context.function());
		assertEquals(0, context.index());
		assertEquals(3, error.location().startLine());
	}

	@Test
	void constructorPatternArgumentMismatchRemainsTypeMismatch() {
		Diagnostic.TypeMismatch error = only(Diagnostic.TypeMismatch.class,
				"""
				type Box = Box I32

				bad value =
				  case value of
				    Box True -> 1
				""");

		assertEquals(Diagnostic.TypeMismatchReason.INCOMPATIBLE, error.reason());
		assertInstanceOf(Diagnostic.TypingContext.None.class, error.context());
		assertEquals(6, error.location().startLine());
	}

	@Test
	void constructorPatternWithDifferentDeclaredAdtFamilyHasDedicatedDiagnostic() {
		Diagnostic.ConstructorFamilyMismatch error = only(Diagnostic.ConstructorFamilyMismatch.class,
				"""
				type Maybe a =
				  | Nothing
				  | Just a

				type Box = Box (Maybe I32)

				bad value =
				  case value of
				    Box True -> 1
				""");

		assertEquals(zlk.common.id.Id.intern("Basic.True"), error.constructor());
		assertEquals(zlk.common.id.Id.intern("Bool"), error.actualFamily());
		assertEquals(zlk.common.id.Id.intern("Main.Maybe"), error.expectedFamily());
		assertEquals(10, error.location().startLine());
	}

	@Test
	void constructorPatternAgainstNonAdtTargetRemainsTypeMismatch() {
		Diagnostic.TypeMismatch error = only(Diagnostic.TypeMismatch.class,
				"""
				bad : I32 -> I32
				bad value =
				  case value of
				    True -> 1
				""");

		assertEquals(Diagnostic.TypeMismatchReason.INCOMPATIBLE, error.reason());
		assertInstanceOf(Diagnostic.TypingContext.None.class, error.context());
		assertEquals(5, error.location().startLine());
	}


	@Test
	void recordUpdateWithoutTheFieldReportsFieldContext() {
		Diagnostic.TypeMismatch error = only(Diagnostic.TypeMismatch.class,
				"""
				setX : { y : Bool } -> I32 -> { y : Bool }
				setX record value = { record | x = value }
				""");

		Diagnostic.TypingContext.FieldAccess context = assertInstanceOf(
				Diagnostic.TypingContext.FieldAccess.class, error.context());
		assertEquals("x", context.field());
		assertEquals(3, error.location().startLine());
	}

	@Test
	void fieldAccessWithoutTheFieldReportsFieldContext() {
		Diagnostic.TypeMismatch error = only(Diagnostic.TypeMismatch.class,
				"""
				getX : { y : Bool } -> I32
				getX record = record.x
				""");

		Diagnostic.TypingContext.FieldAccess context = assertInstanceOf(
				Diagnostic.TypingContext.FieldAccess.class, error.context());
		assertEquals("x", context.field());
		assertEquals(3, error.location().startLine());
	}

	@Test
	void recordUpdateCallerWithoutTheUpdatedFieldIsReported() {
		Diagnostic.TypeMismatch error = only(Diagnostic.TypeMismatch.class,
				"""
				setX record value = { record | x = value }
				bad = setX { y = True } 1
				""");

		assertTrue(error.isError());
	}

	@Test
	void fieldAccessCallerWithoutTheRequestedFieldIsReported() {
		Diagnostic.TypeMismatch error = only(Diagnostic.TypeMismatch.class,
				"""
				getX record = record.x
				bad = getX { y = True }
				""");

		assertTrue(error.isError());
	}

	@Test
	void overlyGeneralAnnotationReportsAnnotationContext() {
		Diagnostic.TypeMismatch error = only(Diagnostic.TypeMismatch.class,
				"""
				bad : a -> a
				bad x = 1
				""");

		Diagnostic.TypingContext.Annotation context = assertInstanceOf(
				Diagnostic.TypingContext.Annotation.class, error.context());
		assertEquals("bad", context.declaration());
		assertEquals(3, error.location().startLine());
	}

	@Test
	void incompatibleMutualAnnotationsReportAnnotationContext() {
		Diagnostic.TypeMismatch error = only(Diagnostic.TypeMismatch.class,
				"""
				f : a -> a
				f x = g x

				g : b -> I32
				g x = f x
				""");

		assertInstanceOf(Diagnostic.TypingContext.Annotation.class, error.context());
		assertEquals(3, error.location().startLine());
	}

	@Test
	void recursiveAnnotationMismatchReportsCallArgumentContext() {
		Diagnostic.TypeMismatch error = only(Diagnostic.TypeMismatch.class,
				"""
				f : a -> a
				f x = f 1
				""");

		assertInstanceOf(Diagnostic.TypingContext.CallArgument.class, error.context());
		assertEquals(3, error.location().startLine());
	}

	@Test
	void localAnnotationCaptureReportsAnnotationContext() {
		Diagnostic.TypeMismatch error = only(Diagnostic.TypeMismatch.class,
				"""
				outer x =
				  let
				    f : a -> a
				    f y = x
				  in
				    f
				""");

		assertInstanceOf(Diagnostic.TypingContext.Annotation.class, error.context());
		assertEquals(4, error.location().startLine());
	}

	@Test
	void invalidSourceDoesNotLeakRuntimeExceptionsFromTheDriver() {
		CompilationFixture module = assertDoesNotThrow(
				() -> CompilationFixture.compile("bad = add True 1\n"));
		assertInstanceOf(Driver.CompilationResult.Failed.class, module.result());
	}

	private static <T extends Diagnostic> T only(Class<T> type, String src) {
		CompilationFixture module = assertDoesNotThrow(() -> CompilationFixture.compile(src));
		assertInstanceOf(Driver.CompilationResult.Failed.class, module.result());
		var diagnostics = module.diagnostics(type);
		assertEquals(1, diagnostics.size());
		T diagnostic = diagnostics.head();
		assertTrue(diagnostic.isError());
		return diagnostic;
	}
}
