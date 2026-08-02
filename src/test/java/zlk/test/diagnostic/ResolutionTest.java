package zlk.test.diagnostic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import zlk.compiler.Driver;
import zlk.diagnostic.Diagnostic;

public class ResolutionTest {
	@Test
	void unknownTypeNameIsRejected() {
		Diagnostic.UnknownName error = only(Diagnostic.UnknownName.class,
				"""
				module Main
				bad : Missing
				bad = 0
				""");

		assertEquals("Missing", error.name());
		assertEquals(2, error.location().startLine());
	}

	@Test
	void aliasArityMismatchIsRejected() {
		Diagnostic.TypeArityMismatch error = only(Diagnostic.TypeArityMismatch.class,
				"""
				module Main
				type alias Box a = { value : a }
				bad : Box
				bad = { value = 1 }
				""");

		assertEquals("Box", error.name());
		assertEquals(1, error.expected());
		assertEquals(0, error.actual());
		assertEquals(3, error.location().startLine());
	}

	@Test
	void nominalTypeArityMismatchIsRejected() {
		Diagnostic.TypeArityMismatch error = only(Diagnostic.TypeArityMismatch.class,
				"""
				module Main
				type Box a = Box a
				bad : Box
				bad = Box 1
				""");

		assertEquals("Box", error.name());
		assertEquals(1, error.expected());
		assertEquals(0, error.actual());
		assertEquals(3, error.location().startLine());
	}

	@Test
	void recursiveAliasReportsCycleAndReferenceLocations() {
		Diagnostic.RecursiveTypeAlias error = only(Diagnostic.RecursiveTypeAlias.class,
				"""
				module Main
				type alias A a = B a
				type alias B a = A a
				bad : A I32
				bad = 0
				""");

		assertEquals("A", error.alias());
		assertEquals(3, error.location().startLine());
		assertEquals(2, error.cycleLocation().startLine());
	}

	@Test
	void duplicateTypeParameterReportsOriginalLocation() {
		Diagnostic.DuplicateTypeParameter error = only(Diagnostic.DuplicateTypeParameter.class,
				"""
				module Main
				type alias Bad a a = { value : a }
				""");

		assertEquals("a", error.name());
		assertEquals(2, error.location().startLine());
		assertEquals(2, error.previousLocation().startLine());
	}

	@Test
	void undeclaredTypeVariableIsRejected() {
		Diagnostic.UndeclaredTypeVariable error = only(Diagnostic.UndeclaredTypeVariable.class,
				"""
				module Main
				type Bad a = Bad b
				""");

		assertEquals("b", error.name());
		assertEquals(2, error.location().startLine());
	}

	@Test
	void rowParameterRejectsValueTypeArgument() {
		Diagnostic.TypeKindMismatch error = only(Diagnostic.TypeKindMismatch.class,
				"""
				module Main
				type alias Foo r = { r | value : I32 }
				bad : Foo I32
				bad = { value = 0 }
				""");

		assertEquals("Foo", error.subject());
		assertEquals(Diagnostic.TypeKind.ROW, error.expectedKind());
		assertEquals(Diagnostic.TypeKind.TYPE, error.actualKind());
		assertEquals(3, error.location().startLine());
	}

	@Test
	void conflictingTypeVariableKindsRetainFirstUseLocation() {
		Diagnostic.TypeKindMismatch error = only(Diagnostic.TypeKindMismatch.class,
				"""
				module Main
				type alias Bad a = { a | value : a }
				""");

		assertEquals("a", error.subject());
		assertEquals(Diagnostic.TypeKind.ROW, error.expectedKind());
		assertEquals(Diagnostic.TypeKind.TYPE, error.actualKind());
		assertEquals(2, error.location().startLine());
		assertEquals(2, error.previousLocation().startLine());
	}

	@Test
	void expandedAliasDuplicateFieldRetainsOriginalLocation() {
		Diagnostic.DuplicateRecordField error = only(Diagnostic.DuplicateRecordField.class,
				"""
				module Main
				type alias Foo r = { r | x : I32 }
				bad : Foo { x : Bool }
				bad = { x = 0 }
				""");

		assertEquals("x", error.fieldName());
		assertEquals(2, error.location().startLine());
		assertEquals(3, error.previousLocation().startLine());
	}

	@Test
	void duplicateTopLevelValueRetainsOriginalLocation() {
		Diagnostic.DuplicateName error = only(Diagnostic.DuplicateName.class,
				"""
				module Main
				x = 1
				x = 2
				""");

		assertEquals(Diagnostic.NameNamespace.VALUE, error.namespace());
		assertEquals("x", error.name());
		assertEquals(3, error.location().startLine());
		assertEquals(2, error.previousLocation().startLine());
	}

	@Test
	void duplicateLocalValueRetainsOriginalLocation() {
		Diagnostic.DuplicateName error = only(Diagnostic.DuplicateName.class,
				"""
				module Main
				f = let
				  x = 1
				  x = 2
				in x
				""");

		assertEquals(Diagnostic.NameNamespace.VALUE, error.namespace());
		assertEquals("x", error.name());
		assertEquals(4, error.location().startLine());
		assertEquals(3, error.previousLocation().startLine());
	}

	@Test
	void duplicateTypeAndAliasRetainOriginalLocation() {
		Diagnostic.DuplicateName error = only(Diagnostic.DuplicateName.class,
				"""
				module Main
				type Thing = Thing
				type alias Thing = I32
				""");

		assertEquals(Diagnostic.NameNamespace.TYPE, error.namespace());
		assertEquals("Thing", error.name());
		assertEquals(3, error.location().startLine());
		assertEquals(2, error.previousLocation().startLine());
	}

	@Test
	void duplicateConstructorRetainsOriginalLocation() {
		Diagnostic.DuplicateName error = only(Diagnostic.DuplicateName.class,
				"""
				module Main
				type One = Same
				type Two = Same
				""");

		assertEquals(Diagnostic.NameNamespace.CONSTRUCTOR, error.namespace());
		assertEquals("Same", error.name());
		assertEquals(3, error.location().startLine());
		assertEquals(2, error.previousLocation().startLine());
	}

	@Test
	void constructorApplicationArityMismatchIsRejected() {
		Diagnostic.ConstructorArityMismatch error = only(Diagnostic.ConstructorArityMismatch.class,
				"""
				module Main
				type Pair = Pair I32 I32
				bad = Pair 1 2 3
				""");

		assertEquals("Pair", error.constructor());
		assertEquals(2, error.expected());
		assertEquals(3, error.actual());
		assertEquals(3, error.location().startLine());
	}

	@Test
	void constructorPatternArityMismatchIsRejected() {
		Diagnostic.ConstructorArityMismatch error = only(Diagnostic.ConstructorArityMismatch.class,
				"""
				module Main
				type Pair = Pair I32 I32
				bad value = case value of
				  Pair x -> x
				""");

		assertEquals("Pair", error.constructor());
		assertEquals(2, error.expected());
		assertEquals(1, error.actual());
		assertEquals(4, error.location().startLine());
	}

	@Test
	void letInThenBranchDoesNotLeakToElseBranch() {
		Diagnostic.UnknownName error = only(Diagnostic.UnknownName.class,
				"""
				module Main
				f n =
				  if isZero n then
				    let
				      one = 1
				    in
				      one
				  else
				    one
				""");

		assertEquals("one", error.name());
		assertEquals(9, error.location().startLine());
	}

	@Test
	void selfRecursiveAliasReportsCycleAndReferenceLocations() {
		Diagnostic.RecursiveTypeAlias error = only(Diagnostic.RecursiveTypeAlias.class,
				"""
				module Main
				type alias Bad a = Bad a
				value : Bad I32
				value = 0
				""");
		assertEquals("Bad", error.alias());
		assertEquals(2, error.location().startLine());
		assertEquals(2, error.cycleLocation().startLine());
	}

	@Test
	void duplicateNominalTypeParameterReportsOriginalLocation() {
		Diagnostic.DuplicateTypeParameter error = only(Diagnostic.DuplicateTypeParameter.class,
				"""
				module Main
				type Bad a a = Bad a
				""");
		assertEquals("a", error.name());
		assertEquals(2, error.location().startLine());
		assertEquals(2, error.previousLocation().startLine());
	}

	@Test
	void aliasBodyUndeclaredTypeVariableIsRejected() {
		Diagnostic.UndeclaredTypeVariable error = only(Diagnostic.UndeclaredTypeVariable.class,
				"""
				module Main
				type alias Bad a = b
				""");
		assertEquals("b", error.name());
		assertEquals(2, error.location().startLine());
	}

	@Test
	void nominalRowParameterRejectsValueTypeArgument() {
		Diagnostic.TypeKindMismatch error = only(Diagnostic.TypeKindMismatch.class,
				"""
				module Main
				type Foo r = Foo { r | value : I32 }
				bad : Foo I32
				bad = Foo { value = 0 }
				""");
		assertEquals("Foo", error.subject());
		assertEquals(Diagnostic.TypeKind.ROW, error.expectedKind());
		assertEquals(Diagnostic.TypeKind.TYPE, error.actualKind());
		assertEquals(3, error.location().startLine());
	}

	@Test
	void annotationKindConflictRetainsFirstUseLocation() {
		Diagnostic.TypeKindMismatch error = only(Diagnostic.TypeKindMismatch.class,
				"""
				module Main
				bad : { a | value : a }
				bad = { value = 0 }
				""");
		assertEquals("a", error.subject());
		assertEquals(Diagnostic.TypeKind.TYPE, error.expectedKind());
		assertEquals(Diagnostic.TypeKind.ROW, error.actualKind());
		assertEquals(2, error.location().startLine());
		assertEquals(2, error.previousLocation().startLine());
	}

	@Test
	void nominalTypeParameterKindConflictRetainsFirstUseLocation() {
		Diagnostic.TypeKindMismatch error = only(Diagnostic.TypeKindMismatch.class,
				"""
				module Main
				type Bad a = AsType a | AsRow { a | value : I32 }
				""");
		assertEquals("a", error.subject());
		assertEquals(Diagnostic.TypeKind.TYPE, error.expectedKind());
		assertEquals(Diagnostic.TypeKind.ROW, error.actualKind());
		assertEquals(2, error.location().startLine());
		assertEquals(2, error.previousLocation().startLine());
	}

	private static <T extends Diagnostic> T only(Class<T> type, String src) {
		Driver.CompilationResult result = Driver.compile("Main.zlk", src);
		Driver.CompilationResult.Failed failed = assertInstanceOf(Driver.CompilationResult.Failed.class, result);
		assertEquals(1, failed.diags().size());
		Diagnostic diagnostic = failed.diags().head();
		assertTrue(diagnostic.isError());
		return assertInstanceOf(type, diagnostic);
	}
}
