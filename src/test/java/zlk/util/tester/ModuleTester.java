package zlk.util.tester;

import zlk.compiler.diagnostic.Diagnostic;
import zlk.compiler.ir.ast.Module;
import zlk.compiler.ir.idcalc.IcModule;
import zlk.compiler.ir.token.Tokenized;
import zlk.compiler.phase.nameeval.NameEvaluator;
import zlk.compiler.phase.parse.Lexer;
import zlk.compiler.phase.parse.Parser;
import zlk.compiler.phase.parse.Parser.ParseResult;
import zlk.compiler.phase.patterncheck.PatternChecker;
import zlk.compiler.phase.recon.Constraint;
import zlk.compiler.phase.recon.ConstraintExtractor;
import zlk.compiler.phase.recon.FreshFlex;
import zlk.compiler.phase.recon.TypeReconstructor;
import zlk.util.collection.IntSeq;
import zlk.util.collection.Seq;
import zlk.util.collection.SeqBuffer;

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

		Tokenized tokens = new Lexer(TARGET_FILE_NAME, this.src).lex();
		ParseResult parsed = Parser.parse(tokens);
		this.ast = parsed.ast();
		this.parseErrors = parsed.errors();
		if(this.compileLevel == CompileLevel.PARSE) {
			return;
		}
		if(!parseErrors.isEmpty()) {
			throw new IllegalStateException("parse errors in line "
					+ parseErrors.map(se -> se.location().startLine()).join(", "));
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

		SeqBuffer<Diagnostic> reconDiagnostics = new SeqBuffer<>();
		var reconed = TypeReconstructor.recon(result, freshFlex, reconDiagnostics::add).fold(
				reconstructed -> reconstructed,
				() -> {
					throw new IllegalStateException("type reconstruction failed: " + reconDiagnostics.toSeq());
				});
		if(this.compileLevel == CompileLevel.TYPE_RECON) {
			return;
		}

		this.patternErrors = PatternChecker.check(module, reconed.partExpType());
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
