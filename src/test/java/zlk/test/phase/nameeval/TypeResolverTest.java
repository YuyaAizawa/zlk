package zlk.test.phase.nameeval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import zlk.compiler.ir.idcalc.IcModule;
import zlk.compiler.ir.typing.RecordField;
import zlk.compiler.ir.typing.Type;
import zlk.util.collection.Seq;
import zlk.util.tester.DumpOnFailureWatcher;
import zlk.util.tester.ModuleTester;
import zlk.util.tester.ModuleTester.CompileLevel;

@ExtendWith(DumpOnFailureWatcher.class)
public class TypeResolverTest {
	@Test
	void aliasNotPresentInIcModuleTypes() {
		var module = new ModuleTester(
				"""
				type alias Box a = { value : a }
				box : Box I32
				box = { value = 1 }
				type Real = Real I32
				""", CompileLevel.NAME_EVAL);
		IcModule ic = module.getIdcalcModule();
		assertTrue(ic.types().anyMatch(d -> d.id().simpleName().equals("Real")));
		assertFalse(ic.types().anyMatch(d -> d.id().simpleName().equals("Box")));
	}

	@Test
	void aliasPreservesOpenRowArgument() {
		var module = new ModuleTester(
				"""
				type alias Foo a = { a | x : I32 }
				keep : Foo a -> Foo a
				keep value = value
				""", CompileLevel.NAME_EVAL);
		Type.Record open = new Type.Record(
				Seq.of(new RecordField<>("x", Type.I32)),
				Optional.of(new Type.RowVar("a")));
		Type annotation = module.getIdcalcModule().decls().head().anno().orElseThrow();
		assertEquals(new Type.Arrow(open, open), annotation);
	}

	@Test
	void adtConstructorArgumentCanUseAlias() {
		var module = new ModuleTester(
				"""
				type alias Box a = { value : a }
				type Wrapped a = Wrapped (Box a)
				""", CompileLevel.NAME_EVAL);
		Type ctorArg = module.getIdcalcModule().types().head().ctors().head().args().head();
		assertEquals(new Type.Record(
				Seq.of(new RecordField<>("value", new Type.Var("a")))), ctorArg);
	}

	@Test
	void adtParameterCanBeUsedAsRowParameter() {
		var module = new ModuleTester(
				"""
				type Foo a = Foo { a | bar : I32 }
				""", CompileLevel.NAME_EVAL);
		var foo = module.getIdcalcModule().types().head();
		Type rowParameter = new Type.Record(
				Seq.of(), Optional.of(new Type.RowVar("a")));
		assertEquals(Seq.of(rowParameter), foo.vars());
		assertEquals(new Type.Record(
				Seq.of(new RecordField<>("bar", Type.I32)),
				Optional.of(new Type.RowVar("a"))), foo.ctors().head().args().head());
	}

	@Test
	void adtRowParameterKindPropagatesAcrossMutuallyRecursiveNominalReferences() {
		var module = new ModuleTester(
				"""
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
		var module = new ModuleTester(
				"""
				type alias Open r = { r | value : I32 }
				type Box r = Box (Open r)
				""", CompileLevel.NAME_EVAL);
		Type rowParameter = new Type.Record(
				Seq.of(), Optional.of(new Type.RowVar("r")));
		assertEquals(Seq.of(rowParameter), module.getIdcalcModule().types().head().vars());
	}

	@Test
	void valueAnnotationImplicitlyIntroducesTypeParameter() {
		var module = new ModuleTester(
				"""
				id : a -> a
				id x = x
				""", CompileLevel.NAME_EVAL);
		Type annoId = module.getIdcalcModule().decls().head().anno().orElseThrow();
		assertEquals(new Type.Arrow(new Type.Var("a"), new Type.Var("a")), annoId);
	}
}
