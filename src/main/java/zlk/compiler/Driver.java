package zlk.compiler;

import java.util.HashMap;
import java.util.Map;

import zlk.ast.Module;
import zlk.bytecodegen.BytecodeGenerator;
import zlk.clcalc.CcModule;
import zlk.clconv.ClosureConverter;
import zlk.common.Type;
import zlk.common.id.Id;
import zlk.common.id.IdMap;
import zlk.core.Builtin;
import zlk.idcalc.IcModule;
import zlk.nameeval.NameEvaluator;
import zlk.parser.Lexer;
import zlk.parser.Parser;
import zlk.parser.Tokenized;
import zlk.patterncheck.PatternChecker;
import zlk.patterncheck.PcError;
import zlk.recon.CaseTyping;
import zlk.recon.ConstraintExtractor;
import zlk.recon.FreshFlex;
import zlk.recon.TypeReconstructor;
import zlk.util.collection.Seq;

public final class Driver {

	// TODO: 出力を副作用にするのか，戻り値にするのか
	// これはCompilePhaseのインターフェースをとりあえずで決めるために作った
	public static PhaseResult<Map<String, byte[]>> compile(String name, String src) {

		Diagnostic.Sink sink = new Diagnostic.Sink();

		// 字句解析から名前解決まで
		PhaseResult<IcModule> nameEvaled = lexPhase(name, src, sink)
				.andThen(tokenized -> parsePhase(tokenized, sink))
				.andThen(module -> nameEvalPhase(module, sink));

		// 型推論
		PhaseResult<TypesAndCaseTypings> reconed =
				nameEvaled.andThen(icModule -> reconPhase(icModule, sink));

		// パターン検査
		PhaseResult<Class<Void>> patternChecked = reconed.andThen(
				typesAndcaseTypings -> nameEvaled.andThen(
				module -> patternPhase(module, typesAndcaseTypings.caseTypings(), sink)));

		// 閉包変換からバイトコード生成まで
		PhaseResult<Map<String, byte[]>> result = nameEvaled.andThen(
				module -> patternChecked.andThen(
				_ -> reconed.andThen(
					typesAndcaseTypings -> closurePhase(module, typesAndcaseTypings.types(), sink)
						.andThen(clcalced -> bytecodePhase(clcalced, typesAndcaseTypings.types(), name, sink)))));

		// TODO: sinkであつめた診断の処理
		return result;
	}

	private static PhaseResult<Tokenized> lexPhase(String name, String src, Diagnostic.Sink sink) {
		return PhaseResult.ready(new Lexer(name, src).lex());
	}

	private static PhaseResult<Module> parsePhase(Tokenized tokens, Diagnostic.Sink sink) {
		return PhaseResult.ready(Parser.parse(tokens));
	}

	private static PhaseResult<IcModule> nameEvalPhase(Module module, Diagnostic.Sink sink) {
		return PhaseResult.ready(new NameEvaluator(module).eval());
	}

	record TypesAndCaseTypings(
			IdMap<Type> types,
			Seq<CaseTyping<Type>> caseTypings
	) {}
	private static PhaseResult<TypesAndCaseTypings> reconPhase(
			IcModule module,
			Diagnostic.Sink sink
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

	private static PhaseResult<Class<Void>> patternPhase(
			IcModule module,
			Seq<CaseTyping<Type>> caseTypings,
			Diagnostic.Sink sink
	) {
		Seq<PcError> result = PatternChecker.check(module, caseTypings);
		if(result.isEmpty()) {
			return PhaseResult.ready(Void.TYPE);
		}
		// 警告はないので1つでも診断があれば即失敗
		result.map(Diagnostic.InvalidPattern::new).forEach(sink::report);
		return PhaseResult.blocked();
	}

	private static PhaseResult<CcModule> closurePhase(
			IcModule module,
			IdMap<Type> types,
			Diagnostic.Sink sink
	) {
		Seq<Id> builtinIds = Builtin.functions().map(Builtin::id);
		return PhaseResult.ready(new ClosureConverter(module, types, builtinIds).convert());
	}

	private static PhaseResult<Map<String, byte[]>> bytecodePhase(
			CcModule module,
			IdMap<Type> types,
			String name,
			Diagnostic.Sink sink
	) {
		Map<String, byte[]> bytecode = new HashMap<>();
		new BytecodeGenerator(module, types, Builtin.functions(), name)
				.compile(bytecode::put);
		return PhaseResult.ready(bytecode);
	}
}
