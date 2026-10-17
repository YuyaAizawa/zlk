package zlk.test.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;

import zlk.compiler.id.Id;
import zlk.compiler.phase.recon.ConstraintExtractor;
import zlk.compiler.phase.recon.FreshFlex;
import zlk.compiler.phase.recon.TypeReconstructor;
import zlk.util.collection.Seq;
import zlk.util.tester.ModuleTester;
import zlk.util.tester.ModuleTester.CompileLevel;

public class TypePrinterTest {
	@Test
	void typePrinterReadsEachReconstructedTermWithoutChangingTypeMaps() {
		var module = new ModuleTester("""
				type alias XY = { y : I32, x : I32 }
				type alias Same a = { y : a, x : a }
				make value = { x = value, y = value }
				concrete = make 10
				""", CompileLevel.NAME_EVAL).getIdcalcModule();
		var fresh = new FreshFlex();
		var inferred = TypeReconstructor.recon(ConstraintExtractor.extract(module, fresh), fresh,
				diagnostic -> fail(diagnostic.toString())).fold(result -> result, () -> fail("reconstruction failed"));
		var printer = new zlk.compiler.presentation.TypePrinter(module.typeAliases());
		var originalTypes = inferred.types().buildString();
		var originalTerms = new java.util.IdentityHashMap<zlk.compiler.ir.idcalc.ExpOrPattern, zlk.compiler.ir.typing.Type>();
		inferred.partExpType().forEach(originalTerms::put);
		var make = module.decls().filter(decl -> decl.id().simpleName().equals("make")).head();
		var concrete = module.decls().filter(decl -> decl.id().simpleName().equals("concrete")).head();
		assertEquals("Main.Same a", printer.convert(inferred.partExpType().get(make.body())).buildString());
		assertEquals("Main.XY", printer.convert(inferred.partExpType().get(concrete.body())).buildString());
		inferred.partExpType().forEach((term, type) -> {
			printer.convert(type);
			org.junit.jupiter.api.Assertions.assertSame(originalTerms.get(term), type);
		});
		assertEquals(originalTypes, inferred.types().buildString());
	}

	@Test
	void displayConversionRepresentsAliasesAsCtorApps() {
		var module = new ModuleTester("""
				type alias Open r a = { r | value : a }
				value = { value = 10, extra = True }
				""", CompileLevel.NAME_EVAL).getIdcalcModule();
		var target = new zlk.compiler.ir.typing.Type.Record(Seq.of(
				new zlk.compiler.ir.typing.RecordField<>("value", zlk.compiler.ir.typing.Type.I32),
				new zlk.compiler.ir.typing.RecordField<>("extra", zlk.compiler.ir.typing.Type.BOOL)));
		var converted = new zlk.compiler.presentation.TypePrinter(module.typeAliases()).convert(target);
		var expected = new zlk.compiler.ir.typing.Type.CtorApp(Id.intern("Main.Open"), Seq.of(
				new zlk.compiler.ir.typing.Type.Record(Seq.of(
						new zlk.compiler.ir.typing.RecordField<>("extra", zlk.compiler.ir.typing.Type.BOOL))),
				zlk.compiler.ir.typing.Type.I32));
		assertEquals(expected, converted);
		assertEquals("Main.Open { extra : Bool } I32", converted.buildString());
		assertEquals("{ extra : Bool, value : I32 }", target.buildString());
	}

	@Test
	void emptyOpenRowAliasTerminatesWhenItsArgumentIsTheWholeTarget() {
		var target = new zlk.compiler.ir.typing.Type.Record(zlk.util.collection.Seq.of(
				new zlk.compiler.ir.typing.RecordField<>("x", zlk.compiler.ir.typing.Type.I32)));
		var alias = new zlk.compiler.ir.typing.TypeAlias(Id.intern("Main.AnyRow"), zlk.util.collection.Seq.of(
				new zlk.compiler.ir.typing.TypeAlias.Parameter("r", zlk.compiler.ir.typing.TypeAlias.Kind.ROW)),
				new zlk.compiler.ir.typing.Type.Record(zlk.util.collection.Seq.of(),
						java.util.Optional.of(new zlk.compiler.ir.typing.Type.RowVar("r"))));
		assertEquals("Main.AnyRow { x : I32 }", new zlk.compiler.presentation.TypePrinter(zlk.util.collection.Seq.of(alias)).convert(target).buildString());
	}

	@Test
	void phantomNamesAvoidTheWholeTargetAndStayIndependentAcrossOccurrences() {
		var alias = new zlk.compiler.ir.typing.TypeAlias(Id.intern("Main.Phantom"), Seq.of(
				new zlk.compiler.ir.typing.TypeAlias.Parameter("unused", zlk.compiler.ir.typing.TypeAlias.Kind.TYPE)),
				zlk.compiler.ir.typing.Type.I32);
		var printer = new zlk.compiler.presentation.TypePrinter(Seq.of(alias));
		var target = zlk.compiler.ir.typing.Type.arrow(new zlk.compiler.ir.typing.Type.Var("a"),
				zlk.compiler.ir.typing.Type.I32, zlk.compiler.ir.typing.Type.I32);
		assertEquals("a -> Main.Phantom b -> Main.Phantom c", printer.convert(target).buildString());
		assertEquals("a -> Main.Phantom b -> Main.Phantom c", printer.convert(target).buildString());
		assertEquals("a -> I32 -> I32", target.buildString());
	}

	@Test
	void unusedRowArgumentsAreFreshRatherThanClosedEmptyRows() {
		var alias = new zlk.compiler.ir.typing.TypeAlias(Id.intern("Main.PhantomRow"), Seq.of(
				new zlk.compiler.ir.typing.TypeAlias.Parameter("unused", zlk.compiler.ir.typing.TypeAlias.Kind.ROW)),
				zlk.compiler.ir.typing.Type.I32);
		var target = zlk.compiler.ir.typing.Type.arrow(new zlk.compiler.ir.typing.Type.Record(Seq.of(),
				java.util.Optional.of(new zlk.compiler.ir.typing.Type.RowVar("a"))), zlk.compiler.ir.typing.Type.I32);
		assertEquals("{ a |  } -> Main.PhantomRow b", new zlk.compiler.presentation.TypePrinter(Seq.of(alias)).convert(target).buildString());
	}

	@Test
	void structuralFallbackPreservesTheExistingTypeStringContract() {
		var printer = new zlk.compiler.presentation.TypePrinter(zlk.util.collection.Seq.of());
		var empty = new zlk.compiler.ir.typing.Type.Record(zlk.util.collection.Seq.of());
		assertEquals(empty, printer.convert(empty));
		assertEquals("{}", printer.convert(empty).buildString());
	}
}
