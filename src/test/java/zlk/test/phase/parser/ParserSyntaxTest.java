package zlk.test.phase.parser;

import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import zlk.compiler.ir.ast.AnType;
import zlk.compiler.ir.ast.Decl;
import zlk.compiler.ir.ast.Pattern;
import zlk.util.collection.Seq;
import zlk.util.tester.DumpOnFailureWatcher;
import zlk.util.tester.ModuleTester;
import zlk.util.tester.ModuleTester.CompileLevel;

@ExtendWith(DumpOnFailureWatcher.class)
public class ParserSyntaxTest {
	@Test
	void parsesNonEmptyFlatRecordPattern() {
		var module = new ModuleTester("pick { x, y } = x", CompileLevel.PARSE);

		Decl.ValDecl decl = assertInstanceOf(Decl.ValDecl.class, module.getAst().decls().head());
		Pattern.Record pattern = assertInstanceOf(Pattern.Record.class, decl.args().head());
		assertEquals(Seq.of("x", "y"), pattern.fields().map(field -> field.name()));
		assertEquals("{ x, y }", pattern.buildString());
	}

	@Test
	void preservesEmptyAndNestedRecordTypes() {
		var module = new ModuleTester(
				"""
				empty : {}
				empty = value
				nested : { outer : { value : I32 } }
				nested = value
				""", CompileLevel.PARSE);

		Decl.ValDecl empty = assertInstanceOf(Decl.ValDecl.class, module.getAst().decls().head());
		AnType.Record emptyType = assertInstanceOf(AnType.Record.class, empty.anno().orElseThrow());
		assertTrue(emptyType.fields().isEmpty());
		assertTrue(emptyType.extension().isEmpty());

		Decl.ValDecl nested = assertInstanceOf(Decl.ValDecl.class, module.getAst().decls().at(1));
		AnType.Record nestedType = assertInstanceOf(AnType.Record.class, nested.anno().orElseThrow());
		assertInstanceOf(AnType.Record.class, nestedType.fields().head().type());
	}

	@Test
	void parsesTypeAliasWithOpenRecordBody() {
		var module = new ModuleTester(
				"type alias Foo a = { a | y : Bool, x : I32 }",
				CompileLevel.PARSE);

		Decl.TypeAlias alias = assertInstanceOf(Decl.TypeAlias.class, module.getAst().decls().head());
		assertEquals("Foo", alias.name());
		assertEquals(Seq.of("a"), alias.vars().map(AnType.Var::name));
		AnType.Record body = assertInstanceOf(AnType.Record.class, alias.body());
		assertEquals("a", body.extension().orElseThrow().name());
		assertEquals(Seq.of("y", "x"), body.fields().map(AnType.RecordField::name));
		assertEquals("type alias Foo a = { a | y : Bool, x : I32 }", alias.buildString());
	}

	@Test
	void parsesClosedAndOpenRecordsAsTypeConstructorArguments() {
		var module = new ModuleTester(
				"""
				type alias Foo a = { a | x : I32 }
				closed : Foo {}
				closed = { x = 1 }
				wider : Foo { z : I32 }
				wider = { x = 1, z = 2 }
				""", CompileLevel.PARSE);

		Decl.ValDecl closed = assertInstanceOf(Decl.ValDecl.class, module.getAst().decls().at(1));
		Decl.ValDecl wider = assertInstanceOf(Decl.ValDecl.class, module.getAst().decls().at(2));
		AnType.Type closedAnno = assertInstanceOf(AnType.Type.class, closed.anno().orElseThrow());
		AnType.Type widerAnno = assertInstanceOf(AnType.Type.class, wider.anno().orElseThrow());
		assertInstanceOf(AnType.Record.class, closedAnno.args().head());
		assertInstanceOf(AnType.Record.class, widerAnno.args().head());
		assertTrue(module.getParseErrors().isEmpty());
	}

	@Test
	void preservesMemberAccessTargetPrecedenceWhenPrettyPrinting() {
		var module = new ModuleTester("value = (f x).y", CompileLevel.PARSE);

		assertTrue(module.getAst().buildString().contains("(f x).y"));
	}
}
