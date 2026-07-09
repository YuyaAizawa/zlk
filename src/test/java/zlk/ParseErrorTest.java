package zlk;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import zlk.tester.ModuleTester;
import zlk.tester.ModuleTester.CompileLevel;
import zlk.util.collection.IntSeq;

public class ParseErrorTest {

	@Test
	void panicBlockBodyConsumesNestedIndentBlocks() {
		String src = """
		bad =
		  ?
		    nested
		      deeper
		after =
		  1
		""";

		var module = new ModuleTester(src, CompileLevel.PARSE);
		IntSeq lines = module.getParseErrorStartLines();

		assertEquals(1, lines.size());
		assertEquals(1, lines.head());  // TODO: 2とできたらいいな
	}

	@Test
	void parseRecoveryContinuesAfterBadBlock() {
		String src = """
		bad =
		  ?
		after =
		  1
		""";

		var module = new ModuleTester(src, CompileLevel.PARSE);
		var errors = module.getParseErrors();

		assertEquals(1, errors.size());
		assertEquals(2, module.getAst().decls().size());
	}

	@Test
	void multipleBadBlocksReportMultipleStartLines() {
		String src = """
		a =
		  ?
		b =
		  ??
		c =
		  1
		""";

		var module = new ModuleTester(src, CompileLevel.PARSE);
		IntSeq lines = module.getParseErrorStartLines();

		assertEquals(2, lines.size());
		assertEquals(2, lines.at(0));
		assertEquals(4, lines.at(1));
	}

	@Test
	void badThenBlockReportsStartLineAndKeepsElseBlock() {
		String src = """
		test x =
		  if x then
		    ?
		      nested
		  else
		    1
		after =
		  2
		""";

		var module = new ModuleTester(src, CompileLevel.PARSE);
		IntSeq lines = module.getParseErrorStartLines();

		assertEquals(1, lines.size());
		assertEquals(1, lines.head());  // TODO: if文用のpanicを作って3行目でエラーを検出する
		assertEquals(2, module.getAst().decls().size());
	}

	@Test
	void badArgumentIsExpressionErrorNotDeclarationError() {
		String src = """
		bad = f ?
		after =
		  1
		""";

		var module = new ModuleTester(src, CompileLevel.PARSE);
		IntSeq lines = module.getParseErrorStartLines();

		assertEquals(1, lines.size());
		assertEquals(1, lines.head());
		assertEquals(2, module.getAst().decls().size());
	}

	@Test
	void badValueDeclarationHeaderRecoversAtNextTopLevelDeclaration() {
		String src = """
		bad a ? = f x
		after =
		  1
		""";

		var module = new ModuleTester(src, CompileLevel.PARSE);
		IntSeq lines = module.getParseErrorStartLines();

		assertEquals(1, lines.size());
		assertEquals(1, lines.head());
		assertEquals(2, module.getAst().decls().size());
	}

	@Test
	void badTypeDeclarationRecoversAtNextTopLevelDeclaration() {
		String src = """
		type Maybe a = | ?
		after =
		  1
		""";

		var module = new ModuleTester(src, CompileLevel.PARSE);
		IntSeq lines = module.getParseErrorStartLines();

		assertEquals(1, lines.size());
		assertEquals(1, lines.head());
		assertEquals(2, module.getAst().decls().size());
	}

	@Test
	void badLetValueDeclarationHeaderRecoversAtNextLocalDeclaration() {
		String src = """
		test =
		  let
		    bad a ? = f x
		    good = 1
		  in
		    good
		after =
		  2
		""";

		var module = new ModuleTester(src, CompileLevel.PARSE);
		IntSeq lines = module.getParseErrorStartLines();

		assertEquals(1, lines.size());
		assertEquals(3, lines.head());
		assertEquals(2, module.getAst().decls().size());
	}

}
