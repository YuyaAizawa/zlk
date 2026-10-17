package zlk.test.diagnostic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import zlk.compiler.diagnostic.Diagnostic;
import zlk.compiler.driver.Driver;

public class ResolutionTest {
	@Test
	void aliasTemplateEquivalencePreservesTypeAndRowSharing() {
		String[][] equivalent = {
				{ "F a = a -> a", "G b = b -> b" },
				{ "F r a = { r | x : a }", "G s b = { s | x : b }" },
				{ "F a = { left : { x : a }, right : a }", "G b = { right : b, left : { x : b } }" },
				{ "F r = { r | x : I32 } -> { r | y : Bool }", "G s = { s | x : I32 } -> { s | y : Bool }" },
				// 実体だけの同値性を検査するため，未使用parameterの個数や位置は問わない．
				{ "F unused = I32", "G = I32" },
				{ "F unused a = a -> a", "G b ignored = b -> b" },
				{ "F a = Box a", "G b = Box b" }
		};
		for (String[] aliases : equivalent) {
			var warnings = duplicateAliases("module Main\ntype Box a = Box a\ntype alias "
					+ aliases[0] + "\ntype alias " + aliases[1] + "\n");
			assertEquals(1, warnings.size(), java.util.Arrays.toString(aliases));
			var warning = warnings.head();
			assertEquals("G", warning.name());
			assertEquals("F", warning.previousName());
			assertEquals(Diagnostic.Severity.WARN, warning.severity());
			assertEquals(4, warning.location().startLine());
			assertEquals(3, warning.previousLocation().startLine());
		}
		String[][] distinct = {
				{ "F a = a -> a", "G a b = a -> b" },
				{ "F a = a -> a", "G = I32 -> I32" },
				{ "F r = { r | x : I32 }", "G = { x : I32 }" },
				{ "F a = { left : { x : a }, right : a }", "G a b = { left : { x : a }, right : b }" },
				{ "F r = { r | x : I32 } -> { r | y : Bool }", "G r s = { r | x : I32 } -> { s | y : Bool }" },
				{ "F r a = { r | x : a }", "G s = { s | x : I32 }" },
				{ "F a = Box a", "G a = Other a" },
				{ "F a = a", "G r = { r | x : I32 }" }
		};
		for (String[] aliases : distinct) {
			for (boolean reverse : new boolean[] { false, true }) {
				assertTrue(duplicateAliases("module Main\ntype Box a = Box a\ntype Other a = Other a\ntype alias "
						+ aliases[reverse ? 1 : 0] + "\ntype alias " + aliases[reverse ? 0 : 1] + "\n").isEmpty(),
						java.util.Arrays.toString(aliases));
			}
		}
	}

	@Test
	void duplicateWarningsDependOnlyOnDeclarationsNotReportingOrUses() {
		String declarations = """
				module Main
				type alias A = I32
				type alias B = A
				type alias F a = a -> a
				type alias G b = b -> b
				type alias XY a b = { x : a, y : b }
				type alias YX c d = { y : d, x : c }
				""";
		var expected = duplicateAliases(declarations);
		assertEquals(zlk.util.collection.Seq.of("B", "G", "YX"), expected.map(Diagnostic.DuplicateTypeAlias::name));
		for (String uses : new String[] { "", "value = 10\n", "value : B\nvalue = 10\n",
				"value : B\nvalue = 10\nid : G I32\nid x = x\nfirst = id value\nsecond = id first\nrecord = YX True second\nagain = record\n" }) {
			for (boolean reportTypes : new boolean[] { false, true }) {
				for (boolean reportBytecode : new boolean[] { false, true }) {
					var result = Driver.compile("aliases.zlk", declarations + uses, zlk.compiler.CompilationOptions.DEFAULT
							.set(zlk.compiler.CompilationOptions.Key.REPORT_INFERRED_TYPES, reportTypes)
							.set(zlk.compiler.CompilationOptions.Key.REPORT_BYTECODE_STMT_ORDER, reportBytecode));
					assertInstanceOf(Driver.CompilationResult.Succeeded.class, result);
					var actual = result.diags().filter(d -> d instanceof Diagnostic.DuplicateTypeAlias)
							.map(d -> (Diagnostic.DuplicateTypeAlias) d);
					assertEquals(expected.size(), actual.size());
					for (int i = 0; i < expected.size(); i++) {
						assertEquals(expected.at(i).name(), actual.at(i).name());
						assertEquals(expected.at(i).previousName(), actual.at(i).previousName());
						assertEquals(expected.at(i).severity(), actual.at(i).severity());
						assertEquals(expected.at(i).location().toString(), actual.at(i).location().toString());
						assertEquals(expected.at(i).previousLocation().toString(), actual.at(i).previousLocation().toString());
					}
				}
			}
		}
	}

	private zlk.util.collection.Seq<Diagnostic.DuplicateTypeAlias> duplicateAliases(String source) {
		var result = Driver.compile("aliases.zlk", source);
		assertInstanceOf(Driver.CompilationResult.Succeeded.class, result);
		return result.diags().filter(d -> d instanceof Diagnostic.DuplicateTypeAlias)
				.map(d -> (Diagnostic.DuplicateTypeAlias) d);
	}

	@Test
	void equivalentAliasesWarnAtLaterDeclarationAndCompilationSucceeds() {
		var result = Driver.compile("display.zlk", """
				module Main
				type alias XY a b = { x : a, y : b }
				type alias YX c d = { y : d, x : c }
				value = { x = 10, y = True }
				""");
		assertInstanceOf(Driver.CompilationResult.Succeeded.class, result);
		var warnings = result.diags().filter(d -> d instanceof Diagnostic.DuplicateTypeAlias);
		assertEquals(1, warnings.size());
		var warning = (Diagnostic.DuplicateTypeAlias) warnings.head();
		assertEquals(Diagnostic.Severity.WARN, warning.severity());
		assertEquals("YX", warning.name());
		assertEquals("XY", warning.previousName());
		assertEquals(3, warning.location().startLine());
		assertEquals(2, warning.previousLocation().startLine());
	}

	@Test
	void incomparableDisplayCandidatesRemainSilentForAnnotatedAndNestedUses() {
		String source = """
				module Main
				type alias X r = { r | x : I32 }
				type alias Y r = { r | y : I32 }
				make a = { inner = { x = a, y = a } }
				concrete = make 10
				explicit : { nested : { x : I32, y : I32 } }
				explicit = { nested = { x = 10, y = 20 } }
				""";
		for (boolean reportTypes : new boolean[] { false, true }) {
			var result = Driver.compile("aliases.zlk", source, zlk.compiler.CompilationOptions.DEFAULT
					.set(zlk.compiler.CompilationOptions.Key.REPORT_INFERRED_TYPES, reportTypes));
			assertInstanceOf(Driver.CompilationResult.Succeeded.class, result);
			assertTrue(result.diags().filter(d -> d.severity() == Diagnostic.Severity.WARN).isEmpty());
		}
	}

	@Test
	void unusedNominalAliasesWarnInDeclarationOrderWithFirstRelatedLocation() {
		var result = Driver.compile("aliases.zlk", """
				module Main
				type alias A = I32
				type alias B = A
				type alias C = I32
				""");
		assertInstanceOf(Driver.CompilationResult.Succeeded.class, result);
		var warnings = result.diags().filter(d -> d instanceof Diagnostic.DuplicateTypeAlias)
				.map(d -> (Diagnostic.DuplicateTypeAlias) d);
		assertEquals(2, warnings.size());
		assertEquals(zlk.util.collection.Seq.of("B", "C"), warnings.map(Diagnostic.DuplicateTypeAlias::name));
		assertEquals(zlk.util.collection.Seq.of(3, 4), warnings.map(d -> d.location().startLine()));
		for (var warning : warnings) {
			assertEquals(Diagnostic.Severity.WARN, warning.severity());
			assertEquals("A", warning.previousName());
			assertEquals(2, warning.previousLocation().startLine());
		}
	}

	@Test
	void recordAliasConstructorNameCollisionRetainsBothLocations() {
		for (boolean aliasFirst : new boolean[] { true, false }) {
			String alias = "type alias Box = { value : I32 }\n";
			String adt = "type Choice = Box I32\n";
			Diagnostic.DuplicateName error = only(Diagnostic.DuplicateName.class,
					"module Main\n" + (aliasFirst ? alias + adt : adt + alias));
			assertEquals("Box", error.name());
			assertEquals(aliasFirst ? Diagnostic.NameNamespace.CONSTRUCTOR : Diagnostic.NameNamespace.VALUE,
					error.namespace());
			assertEquals(3, error.location().startLine());
			assertEquals(2, error.previousLocation().startLine());
		}
	}

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
