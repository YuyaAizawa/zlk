package zlk.util.tester;

import zlk.diagnostic.Diagnostic;
import zlk.ir.ast.Module;
import zlk.ir.idcalc.IcModule;
import zlk.ir.token.Tokenized;
import zlk.phase.nameeval.NameEvaluator;
import zlk.phase.patterncheck.PatternChecker;
import zlk.phase.recon.ConstraintExtractor;
import zlk.phase.recon.FreshFlex;
import zlk.phase.recon.TypeReconstructor;
import zlk.phase.recon.constraint.Constraint;
import zlk.util.collection.IntSeq;
import zlk.util.collection.Seq;

public class ModuleTester {

	public enum CompileLevel {
		PARSE,
		NAME_EVAL,
		TYPE_CINT,
		TYPE_RECON,
		PATTERN_CHECK,
		;

		public boolean includes(CompileLevel other) {
			return this.compareTo(other) >= 0;
		}
	}

	private static final String TARGET_MODULE_NAME = "Main";
	private static final String TARGET_FILE_NAME = "Main.zlk";
	private final CompileLevel compileLevel;
	private final String src;

	// CompileLevelに応じて用意するもの
	private Module ast = null;
	private Seq<Diagnostic.SyntaxError> parseErrors = null;
	private IcModule module = null;
	private Constraint cint = null;
	private Seq<Diagnostic> patternErrors = null;

	public ModuleTester(String src, CompileLevel level) {
		this.compileLevel = level;
		this.src = "module " + TARGET_MODULE_NAME + "\n" + src;  // TODO: これ要る？

		Tokenized tokens = new zlk.phase.parse.Lexer(TARGET_FILE_NAME, this.src).lex();
		var parsed = zlk.phase.parse.Parser.parseResult(tokens);
		this.ast = parsed.module();
		this.parseErrors = parsed.diagnostics();

		if(this.compileLevel == CompileLevel.PARSE) {
			return;
		}
		if(!parseErrors.isEmpty()) {
			throw new IllegalStateException("parse errors in line "
					+ parseErrors.map(error -> error.location().startLine()).join(", "));
		}

		this.module = new NameEvaluator(ast).eval();
		if(this.compileLevel == CompileLevel.NAME_EVAL) {
			return;
		}

		FreshFlex freshFlex = new FreshFlex();
		var result = ConstraintExtractor.extract(module, freshFlex);
		this.cint = result.constraint();
		if(this.compileLevel == CompileLevel.TYPE_CINT) {
			return;
		}

		var reconed = TypeReconstructor.recon(result, freshFlex);
		if(this.compileLevel == CompileLevel.TYPE_RECON) {
			return;
		}

		this.patternErrors = PatternChecker.check(module);
		if(this.compileLevel == CompileLevel.PATTERN_CHECK) {
			return;
		}
	}

	public Module getAst() {
		return ast;
	}

	public Constraint getConstraint() {
		return this.cint;
	}

	public IcModule getIdcalcModule() {
		return module;
	}

	// 当面は行数だけ使うので使わない
	public Seq<Diagnostic.SyntaxError> getParseErrors() {
		return parseErrors;
	}
	public IntSeq getParseErrorStartLines() {
		return parseErrors.mapToInt(err -> err.location().startLine() - 1);  // module Mainの分を引く
	}

	public Seq<Diagnostic> getPatternErrors() {
		return patternErrors;
	}

}
