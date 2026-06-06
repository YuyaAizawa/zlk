package zlk;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import zlk.ast.Exp;
import zlk.tester.ModuleTester;
import zlk.tester.ModuleTester.CompileLevel;
import zlk.util.collection.IntSeq;
import zlk.util.collection.Seq;

public class ParseErrorTest {

	@Test
	void panicBlockBodyConsumesNestedIndentBlocks() {
		String src = """
		bad =
		  ?
		    nested
		      deeper
		  stillBad
		after =
		  1
		""";

		var module = new ModuleTester(src, CompileLevel.PARSE);
		IntSeq lines = module.getParseErrorStartLines();

		assertEquals(1, lines.size());
		assertEquals(2, lines.head());
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
		Seq<Exp.Err> errors = module.getParseErrors();

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
		assertEquals(3, lines.head());
		assertEquals(2, module.getAst().decls().size());
	}
}