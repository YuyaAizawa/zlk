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
import zlk.ir.reuse.anf.AnfModule;
import zlk.ir.reuse.own.OwnModule;
import zlk.ir.reuse.plan.ReusePlan;
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
import zlk.phase.recon.TypeReconstructor;
import zlk.phase.reuse.AnfConverter;
import zlk.phase.reuse.OwnershipElaborator;
import zlk.phase.reuse.ReusePlanner;
import zlk.util.collection.Seq;
import zlk.util.collection.SeqBuffer;

public final class Driver {

	/**
	 * コンパイル時に任意で有効化する診断設定．
	 */
	public record CompilationOptions(
			boolean reportInferredTypes,
			boolean reportBytecodeStmtOrder
	) {
		public static final CompilationOptions DEFAULT = new CompilationOptions(false, false);

		public CompilationOptions reportInferredTypes(boolean enabled) {
			return new CompilationOptions(enabled, reportBytecodeStmtOrder);
		}

		public CompilationOptions reportBytecodeStmtOrder(boolean enabled) {
			return new CompilationOptions(reportInferredTypes, enabled);
		}
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
		DiagnosticReporter reconSink = options.reportInferredTypes()
				? diagCollector
				: diagCollector.excluding(Diagnostic.InferredType.class);

		// 字句解析から名前解決まで
		PhaseResult<IcModule> nameEvaled = lexPhase(name, src, diagCollector)
				.andThen(tokenized -> parsePhase(tokenized, diagCollector))
				.andThen(module -> nameEvalPhase(module, diagCollector));

		// 型推論
		PhaseResult<ReconResult> reconed =
				nameEvaled.andThen(icModule -> reconPhase(icModule, reconSink));

		// パターン検査
		PhaseResult<PhaseResult.Unit> patternChecked = reconed.andThen(
				reconResult -> nameEvaled.andThen(
				module -> patternPhase(module, reconResult.partExpTypes(), diagCollector)));

		// 閉包変換
		PhaseResult<CcModule> closured = nameEvaled.andThen(
				module -> patternChecked.andThen(
				_ -> reconed.andThen(
				inffered -> closurePhase(
							module,
							inffered.types(),
							inffered.partExpTypes(),
							diagCollector)
				)));

		// 再利用最適化
		PhaseResult<ReusePlan> planed = closured.andThen(
				ccmodule -> reusePhase(ccmodule, diagCollector));

		DiagnosticReporter bytecodeSink = options.reportBytecodeStmtOrder()
				? diagCollector
				: diagCollector.excluding(Diagnostic.BytecodeStmt.class);
		PhaseResult<Map<String, byte[]>> result =
				planed.andThen(plan ->
				reconed.andThen(inffered ->
					bytecodePhase(plan, inffered.types(), name, bytecodeSink)));

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
			DiagnosticReporter sink
	) {
		// 共通のフレッシュ変数カウンタ
		FreshFlex freshFlex = new FreshFlex();

		// 型制約抽出部
		ConstraintExtractor.Result extracted = ConstraintExtractor.extract(module, freshFlex);

		// 型推論部
		return TypeReconstructor.recon(extracted, freshFlex, sink).andThen(reconed -> {
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

			reportInferredTypes(module, types, sink);

			return PhaseResult.ready(
					new ReconResult(types, reconed.partExpType()));
		});
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
		// TODO: 診断仕込み
		Seq<Id> builtinIds = Builtin.functions().map(Builtin::id);
		return PhaseResult.ready(
				new ClosureConverter(module, types, partExpTypes, builtinIds).convert());
	}

	private static PhaseResult<ReusePlan> reusePhase(
			CcModule module,
			DiagnosticReporter sink
	) {
		// TODO: 診断仕込み
		AnfModule anf = AnfConverter.convert(module);
		OwnModule own = OwnershipElaborator.convert(anf);
		ReusePlan plan = ReusePlanner.plan(own);
		return PhaseResult.ready(plan);
	}


	private static PhaseResult<Map<String, byte[]>> bytecodePhase(
			ReusePlan module,
			IdMap<Type> types,
			String name,
			DiagnosticReporter sink
	) {
		Map<String, byte[]> bytecode = new HashMap<>();
		new BytecodeGenerator(module, types, Builtin.functions(), name, sink)
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
