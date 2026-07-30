package zlk.phase.nameeval;

import org.junit.jupiter.api.extension.ExtendWith;
import zlk.tester.DumpOnFailureWatcher;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import zlk.common.RecordField;
import zlk.common.Type;
import zlk.idcalc.IcModule;
import zlk.tester.ModuleTester;
import zlk.tester.ModuleTester.CompileLevel;
import zlk.util.collection.Seq;

@ExtendWith(DumpOnFailureWatcher.class)
public class TypeResolverTest {
	@Test
	void aliasNotPresentInIcModuleTypes() {
		String src = """
				type alias Box a = { value : a }
				box : Box I32
				box = { value = 1 }
				type Real = Real I32
				""";
		var module = new ModuleTester(src, CompileLevel.NAME_EVAL);
		IcModule ic = module.getIdcalcModule();
		assertTrue(ic.types().anyMatch(d -> d.id().simpleName().equals("Real")));
		assertFalse(ic.types().anyMatch(d -> d.id().simpleName().equals("Box")));
	}

	@Test
	void selfRecursiveAliasIsRejected() {
		assertThrows(RuntimeException.class, () -> new ModuleTester("""
				type alias Bad a = Bad a
				t : Bad I32
				t = 0
				""", CompileLevel.NAME_EVAL));
	}

	@Test
	void mutuallyRecursiveAliasIsRejected() {
		assertThrows(RuntimeException.class, () -> new ModuleTester("""
				type alias A a = B a
				type alias B a = A a
				t : A I32
				t = 0
				""", CompileLevel.NAME_EVAL));
	}

	@Test
	void aliasArityMismatchIsRejected() {
		// Boxはarity 1だが0引数適用
		assertThrows(RuntimeException.class, () -> new ModuleTester("""
				type alias Box a = { value : a }
				bad : Box
				bad = { value = 1 }
				""", CompileLevel.NAME_EVAL));
	}

	@Test
	void aliasPreservesOpenRowArgument() {
		var module = new ModuleTester("""
				type alias Foo a = { a | x : I32 }
				keep : Foo a -> Foo a
				keep value = value
				""", CompileLevel.NAME_EVAL);
		Type.Record open = new Type.Record(
				Seq.of(new RecordField<>("x", Type.I32)),
				java.util.Optional.of(new Type.RowVar("a")));
		Type annotation = module.getIdcalcModule().decls().head().anno().orElseThrow();
		assertEquals(new Type.Arrow(open, open), annotation);
	}

	@Test
	void adtConstructorArgumentCanUseAlias() {
		var module = new ModuleTester("""
				type alias Box a = { value : a }
				type Wrapped a = Wrapped (Box a)
				""", CompileLevel.NAME_EVAL);
		Type ctorArg = module.getIdcalcModule().types().head().ctors().head().args().head();
		assertEquals(new Type.Record(
				Seq.of(new RecordField<>("value", new Type.Var("a")))), ctorArg);
	}

	@Test
	void unknownTypeNameIsRejected() {
		assertThrows(RuntimeException.class, () -> new ModuleTester("""
				bad : Missing
				bad = 0
				""", CompileLevel.NAME_EVAL));
	}

	@Test
	void rowAliasRejectsValueTypeArgument() {
		assertThrows(RuntimeException.class, () -> new ModuleTester("""
				type alias Foo a = { a | x : I32 }
				bad : Foo I32
				bad = { x = 0 }
				""", CompileLevel.NAME_EVAL));
	}

	@Test
	void aliasParameterCannotHaveBothKinds() {
		assertThrows(RuntimeException.class, () -> new ModuleTester("""
				type alias Bad a = { a | value : a }
				""", CompileLevel.NAME_EVAL));
	}

	@Test
	void annotationVariableCannotHaveBothKinds() {
		assertThrows(RuntimeException.class, () -> new ModuleTester("""
				bad : { a | value : a }
				bad = { value = 0 }
				""", CompileLevel.NAME_EVAL));
	}

	@Test
	void aliasExpansionRejectsDuplicateLabel() {
		assertThrows(RuntimeException.class, () -> new ModuleTester("""
				type alias Foo a = { a | x : I32 }
				bad : Foo { x : Bool }
				bad = { x = 0 }
				""", CompileLevel.NAME_EVAL));
	}

	@Test
	void adtParameterCanBeUsedAsRowParameter() {
		var module = new ModuleTester("""
				type Foo a = Foo { a | bar : I32 }
				""", CompileLevel.NAME_EVAL);

		var foo = module.getIdcalcModule().types().head();
		Type rowParameter = new Type.Record(
				Seq.of(), Optional.of(new Type.RowVar("a")));
		assertEquals(Seq.of(rowParameter), foo.vars());

		Type ctorArg = foo.ctors().head().args().head();
		assertEquals(new Type.Record(
				Seq.of(new RecordField<>("bar", Type.I32)),
				Optional.of(new Type.RowVar("a"))), ctorArg);
	}

	@Test
	void adtRowParameterKindPropagatesAcrossMutuallyRecursiveNominalReferences() {
		var module = new ModuleTester("""
				type A r = A (B r)
				type B r = B (A r) | BEnd { r | value : I32 }
				""", CompileLevel.NAME_EVAL);

		Type rowParameter = new Type.Record(
				Seq.of(), Optional.of(new Type.RowVar("r")));
		module.getIdcalcModule().types().forEach(type ->
				assertEquals(Seq.of(rowParameter), type.vars()));
	}

	@Test
	void adtRowParameterKindPropagatesThroughAlias() {
		var module = new ModuleTester("""
				type alias Open r = { r | value : I32 }
				type Box r = Box (Open r)
				""", CompileLevel.NAME_EVAL);

		Type rowParameter = new Type.Record(
				Seq.of(), Optional.of(new Type.RowVar("r")));
		assertEquals(Seq.of(rowParameter), module.getIdcalcModule().types().head().vars());
	}

	@Test
	void adtParameterCannotHaveDifferentKindsAcrossConstructors() {
		assertThrows(RuntimeException.class, () -> new ModuleTester("""
				type Bad a = AsType a | AsRow { a | value : I32 }
				""", CompileLevel.NAME_EVAL));
	}

	@Test
	void adtRowParameterRejectsValueTypeArgument() {
		assertThrows(RuntimeException.class, () -> new ModuleTester("""
				type Foo r = Foo { r | value : I32 }
				bad : Foo I32
				bad = Foo { value = 0 }
				""", CompileLevel.NAME_EVAL));
	}

	@Test
	void duplicateAliasParameterIsRejected() {
		// duplicate alias parameterをMap初期化で黙って上書きせず検出する．
		assertThrows(RuntimeException.class, () -> new ModuleTester("""
				type alias Bad a a = { x : a }
				""", CompileLevel.NAME_EVAL));
	}

	@Test
	void adtConstructorRejectsUndeclaredTypeParameter() {
		// type Bad a = Bad b — bはADT parameterとして宣言されていないため，
		// ADT constructor引数文脈では未宣言変数を暗黙導入せず拒否する．
		assertThrows(RuntimeException.class, () -> new ModuleTester("""
				type Bad a = Bad b
				""", CompileLevel.NAME_EVAL));
	}

	@Test
	void adtConstructorRejectsDuplicateTypeParameter() {
		// type Bad a a = Bad a — ADT parameterの重複宣言を拒否する．
		assertThrows(RuntimeException.class, () -> new ModuleTester("""
				type Bad a a = Bad a
				""", CompileLevel.NAME_EVAL));
	}

	@Test
	void aliasBodyRejectsUndeclaredTypeParameter() {
		// type alias Bad a = b — alias body文脈では未宣言変数 b を拒否する．
		assertThrows(RuntimeException.class, () -> new ModuleTester("""
				type alias Bad a = b
				""", CompileLevel.NAME_EVAL));
	}

	@Test
	void valueAnnotationImplicitlyIntroducesTypeParameter() {
		// value annotation文脈では未宣言変数を暗黙導入する．
		// id : a -> a の a はTYPEとして導入される．
		var module = new ModuleTester("""
				id : a -> a
				id x = x
				""", CompileLevel.NAME_EVAL);
		Type annoId = module.getIdcalcModule().decls().head().anno().orElseThrow();
		assertEquals(new Type.Arrow(new Type.Var("a"), new Type.Var("a")), annoId);
	}

	// ===== alias/backend end-to-endテスト群 =====
}
