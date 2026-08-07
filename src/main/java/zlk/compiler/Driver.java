package zlk.compiler;

import java.util.HashMap;
import java.util.Map;

import zlk.common.Location;
import zlk.common.Type;
import zlk.common.id.Id;
import zlk.common.id.IdMap;
import zlk.core.Builtin;
import zlk.diagnostic.Diagnostic;
import zlk.diagnostic.DiagnosticReporter;
import zlk.ir.ast.Module;
import zlk.ir.clcalc.CcModule;
import zlk.ir.idcalc.IcExp;
import zlk.ir.idcalc.IcModule;
import zlk.ir.token.Tokenized;
import zlk.phase.PhaseResult;
import zlk.phase.clconv.ClosureConverter;
import zlk.phase.codegen.BytecodeGenerator;
import zlk.phase.nameeval.NameEvaluator;
import zlk.phase.parse.Lexer;
import zlk.phase.parse.Parser;
import zlk.phase.patterncheck.PatternChecker;
import zlk.phase.recon.ConstraintExtractor;
import zlk.phase.recon.ExpOrPatternMap;
import zlk.phase.recon.FreshFlex;
import zlk.phase.recon.TypeError;
import zlk.phase.recon.TypeErrorException;
import zlk.phase.recon.TypeReconstructor;
import zlk.phase.recon.constraint.Context;
import zlk.util.collection.Seq;
import zlk.util.collection.SeqBuffer;

public final class Driver {

	/**
	 * コンパイル時に任意で有効化する診断設定．
	 */
	public record CompilationOptions(boolean reportInferredTypes) {
		public static final CompilationOptions DEFAULT = new CompilationOptions(false);
	}

	public sealed interface CompilationResult {

		public Seq<Diagnostic> diags();

		public record Succeeded(
				Map<String, byte[]> clazzes,
				Seq<Diagnostic> diags
		) implements CompilationResult {}

		public record Failed(
				Seq<Diagnostic> diags
		) implements CompilationResult {}
	}

	public static CompilationResult compile(String name, String src) {
		return compile(name, src, CompilationOptions.DEFAULT);
	}

	public static CompilationResult compile(String name, String src, CompilationOptions options) {

		DiagnosticCollector diagCollector = new DiagnosticCollector();

		// 字句解析から名前解決まで
		PhaseResult<IcModule> nameEvaled = lexPhase(name, src, diagCollector)
				.andThen(tokenized -> parsePhase(tokenized, diagCollector))
				.andThen(module -> nameEvalPhase(module, diagCollector));

		// 型推論
		PhaseResult<ReconResult> reconed =
				nameEvaled.andThen(icModule -> reconPhase(icModule, options, diagCollector));

		// パターン検査
		PhaseResult<PhaseResult.Unit> patternChecked = reconed.andThen(
				reconResult -> nameEvaled.andThen(
				module -> patternPhase(module, reconResult.partExpTypes(), diagCollector)));

		// 閉包変換からバイトコード生成まで
		PhaseResult<Map<String, byte[]>> result = nameEvaled.andThen(
				module -> patternChecked.andThen(
				_ -> reconed.andThen(
				inffered -> {
					return closurePhase(
							module,
							inffered.types(),
							inffered.partExpTypes(),
							diagCollector)
						.andThen(clcalced -> bytecodePhase(clcalced, inffered.types(), name, diagCollector));
				})));

		Seq<Diagnostic> diags = diagCollector.collect();
		return result.fold(
				clazzes -> new CompilationResult.Succeeded(clazzes, diags),
				() -> new CompilationResult.Failed(diags));
	}

	private static PhaseResult<Tokenized> lexPhase(String name, String src, DiagnosticReporter sink) {
		return PhaseResult.ready(new Lexer(name, src).lex());
	}

	private static PhaseResult<Module> parsePhase(Tokenized tokens, DiagnosticReporter sink) {
		Parser.Result result = Parser.parseResult(tokens);
		result.diagnostics().forEach(sink::report);
		return result.diagnostics().isEmpty()
				? PhaseResult.ready(result.module())
				: PhaseResult.blocked();
	}

	private static PhaseResult<IcModule> nameEvalPhase(Module module, DiagnosticReporter sink) {
		return new NameEvaluator(module).eval(sink);
	}

	record ReconResult(
			IdMap<Type> types,
			ExpOrPatternMap<Type> partExpTypes
	) {}
	private static PhaseResult<ReconResult> reconPhase(
			IcModule module,
			CompilationOptions options,
			DiagnosticReporter sink
	) {
		// 共通のフレッシュ変数カウンタ
		FreshFlex freshFlex = new FreshFlex();

		// 型制約抽出部
		ConstraintExtractor.Result extracted = ConstraintExtractor.extract(module, freshFlex);

		// 型推論部
		TypeReconstructor.Result reconed;
		try {
			reconed = TypeReconstructor.recon(extracted, freshFlex);
		} catch(TypeErrorException error) {
			sink.report(toDiagnostic(error.error()));
			return PhaseResult.blocked();
		}

		// 組込み型とコンストラクタを追加
		IdMap<Type> types = new IdMap<>();
		Builtin.functions().forEach(builtin -> types.put(builtin.id(), builtin.type()));
		module.types().forEach(
				union -> union.ctors().forEach(
						ctor -> {
							Type ctorApp = new Type.CtorApp(union.id(), union.vars());
							types.put(
									ctor.id(),
									ctor.args().isEmpty()
										? ctorApp
										: Type.arrow(ctor.args(), ctorApp)
							);
						}));
		reconed.types().forEach(types::put);

		if(options.reportInferredTypes()) {
			reportInferredTypes(module, types, sink);
		}

		return PhaseResult.ready(
				new ReconResult(types, reconed.partExpType()));
	}

	private static void reportInferredTypes(
			IcModule module,
			IdMap<Type> types,
			DiagnosticReporter sink
	) {
		module.types().forEach(type ->
				type.ctors().forEach(ctor ->
					reportInferredType(ctor.loc(), ctor.id(), types, sink)));
		module.decls().forEach(decl -> {
			reportInferredType(decl.loc(), decl.id(), types, sink);
			decl.body().walk(exp -> {
				if(exp instanceof IcExp.IcLet let) {
					let.defs().forEach(local ->
						reportInferredType(local.loc(), local.id(), types, sink));
				}
			});
		});
	}

	private static void reportInferredType(
			Location location,
			Id declaration,
			IdMap<Type> types,
			DiagnosticReporter sink
	) {
		Type type = types.getOptional(declaration).orElseThrow(() -> new IllegalStateException(
				"missing reconstructed type for declaration: " + declaration));
		sink.report(new Diagnostic.InferredType(location, declaration, type));
	}

	private static Diagnostic toDiagnostic(TypeError error) {
		return switch(error) {
		case TypeError.InfiniteType(var location, var id) ->
			new Diagnostic.InfiniteType(location, id.simpleName());
		case TypeError.UnificationFailure(var provenance, var reason) ->
			new Diagnostic.TypeMismatch(provenance.location(), toDiagnosticContext(provenance.context()),
					Diagnostic.TypeMismatchReason.valueOf(reason.name()));
		};
	}

	private static Diagnostic.TypingContext toDiagnosticContext(Context context) {
		return switch(context) {
		case Context.Annotation(var id) -> new Diagnostic.TypingContext.Annotation(id.simpleName());
		case Context.CallArg(var maybeId, var index) ->
			new Diagnostic.TypingContext.CallArgument(maybeId.map(id -> id.simpleName()).orElse(""), index);
		case Context.CallArity(var maybeId, var length) ->
			new Diagnostic.TypingContext.CallArity(maybeId.map(id -> id.simpleName()).orElse(""), length);
		case Context.FieldAccess(var field) -> new Diagnostic.TypingContext.FieldAccess(field);
		case Context.IfCondition _ -> new Diagnostic.TypingContext.IfCondition();
		case Context.None _ -> new Diagnostic.TypingContext.None();
		};
	}

	private static PhaseResult<PhaseResult.Unit> patternPhase(
			IcModule module,
			ExpOrPatternMap<Type> patternTypes,
			DiagnosticReporter diagCollector
	) {
		Seq<Diagnostic> result = PatternChecker.check(module, patternTypes);
		result.forEach(diagCollector::report);
		return result.anyMatch(Diagnostic::isError)
				? PhaseResult.blocked()
				: PhaseResult.ready(PhaseResult.Unit.INSTANCE);
	}

	private static PhaseResult<CcModule> closurePhase(
			IcModule module,
			IdMap<Type> types,
			ExpOrPatternMap<Type> partExpTypes,
			DiagnosticReporter sink
	) {
		Seq<Id> builtinIds = Builtin.functions().map(Builtin::id);
		return PhaseResult.ready(
				new ClosureConverter(module, types, partExpTypes, builtinIds).convert());
	}

	private static PhaseResult<Map<String, byte[]>> bytecodePhase(
			CcModule module,
			IdMap<Type> types,
			String name,
			DiagnosticReporter sink
	) {
		Map<String, byte[]> bytecode = new HashMap<>();
		new BytecodeGenerator(module, types, Builtin.functions(), name)
				.compile(bytecode::put);
		return PhaseResult.ready(bytecode);
	}
}

final class DiagnosticCollector implements DiagnosticReporter {

	private SeqBuffer<Diagnostic> acc = new SeqBuffer<>();

	@Override
	public void report(Diagnostic diag) {
		acc.add(diag);
	}

	Seq<Diagnostic> collect() {
		return acc.toSeq();
	}
}
