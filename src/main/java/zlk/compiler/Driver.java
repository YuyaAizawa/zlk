package zlk.compiler;

import java.util.HashMap;
import java.util.Map;

import zlk.common.Type;
import zlk.common.id.Id;
import zlk.common.id.IdMap;
import zlk.core.Builtin;
import zlk.diagnostic.Diagnostic;
import zlk.diagnostic.DiagnosticReporter;
import zlk.ir.ast.Module;
import zlk.ir.clcalc.CcModule;
import zlk.ir.idcalc.IcModule;
import zlk.ir.token.Tokenized;
import zlk.ir.typing.CaseTyping;
import zlk.phase.PhaseResult;
import zlk.phase.clconv.ClosureConverter;
import zlk.phase.codegen.BytecodeGenerator;
import zlk.phase.nameeval.NameEvaluator;
import zlk.phase.parse.Lexer;
import zlk.phase.parse.Parser;
import zlk.phase.patterncheck.PatternChecker;
import zlk.phase.recon.ConstraintExtractor;
import zlk.phase.recon.FreshFlex;
import zlk.phase.recon.TypeReconstructor;
import zlk.util.collection.Seq;
import zlk.util.collection.SeqBuffer;

public final class Driver {

	public sealed interface CompilationResult {

		public record Succeeded(
				Map<String, byte[]> clazzes,
				Seq<Diagnostic> diags
		) implements CompilationResult {}

		public record Failed(
				Seq<Diagnostic> diags
		) implements CompilationResult {}
	}

	public static CompilationResult compile(String name, String src) {

		DiagnosticCollector diagCollector = new DiagnosticCollector();

		// 字句解析から名前解決まで
		PhaseResult<IcModule> nameEvaled = lexPhase(name, src, diagCollector)
				.andThen(tokenized -> parsePhase(tokenized, diagCollector))
				.andThen(module -> nameEvalPhase(module, diagCollector));

		// 型推論
		PhaseResult<TypesAndCaseTypings> reconed =
				nameEvaled.andThen(icModule -> reconPhase(icModule, diagCollector));

		// パターン検査
		PhaseResult<PhaseResult.Unit> patternChecked = reconed.andThen(
				typesAndcaseTypings -> nameEvaled.andThen(
				module -> patternPhase(module, typesAndcaseTypings.caseTypings(), diagCollector)));

		// 閉包変換からバイトコード生成まで
		PhaseResult<Map<String, byte[]>> result = nameEvaled.andThen(
				module -> patternChecked.andThen(
				_ -> reconed.andThen(
					typesAndcaseTypings -> closurePhase(module, typesAndcaseTypings.types(), diagCollector)
						.andThen(clcalced -> bytecodePhase(clcalced, typesAndcaseTypings.types(), name, diagCollector)))));

		Seq<Diagnostic> diags = diagCollector.collect();
		return result.fold(
				clazzes -> new CompilationResult.Succeeded(clazzes, diags),
				() -> new CompilationResult.Failed(diags));
	}

	private static PhaseResult<Tokenized> lexPhase(String name, String src, DiagnosticReporter sink) {
		return PhaseResult.ready(new Lexer(name, src).lex());
	}

	private static PhaseResult<Module> parsePhase(Tokenized tokens, DiagnosticReporter sink) {
		return PhaseResult.ready(Parser.parse(tokens));
	}

	private static PhaseResult<IcModule> nameEvalPhase(Module module, DiagnosticReporter sink) {
		return PhaseResult.ready(new NameEvaluator(module).eval());
	}

	record TypesAndCaseTypings(
			IdMap<Type> types,
			Seq<CaseTyping<Type>> caseTypings
	) {}
	private static PhaseResult<TypesAndCaseTypings> reconPhase(
			IcModule module,
			DiagnosticReporter sink
	) {
		// TODO: せっかく2段階で型推論をしているので気の利いたエラーを考える

		// 共通のフレッシュ変数カウンタ
		FreshFlex freshFlex = new FreshFlex();

		// 型制約抽出部
		ConstraintExtractor.Result extracted = ConstraintExtractor.extract(module, freshFlex);

		// 型推論部
		TypeReconstructor.Result reconed = TypeReconstructor.recon(extracted, freshFlex);

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

		return PhaseResult.ready(new TypesAndCaseTypings(types, reconed.caseTypings()));
	}

	private static PhaseResult<PhaseResult.Unit> patternPhase(
			IcModule module,
			Seq<CaseTyping<Type>> caseTypings,
			DiagnosticReporter diagCollector
	) {
		Seq<Diagnostic> result = PatternChecker.check(module, caseTypings);
		if(result.isEmpty()) {
			return PhaseResult.ready(PhaseResult.Unit.INSTANCE);
		}
		// 警告はないので1つでも診断があれば即失敗
		result.forEach(diagCollector::report);
		return PhaseResult.blocked();
	}

	private static PhaseResult<CcModule> closurePhase(
			IcModule module,
			IdMap<Type> types,
			DiagnosticReporter sink
	) {
		Seq<Id> builtinIds = Builtin.functions().map(Builtin::id);
		return PhaseResult.ready(new ClosureConverter(module, types, builtinIds).convert());
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
