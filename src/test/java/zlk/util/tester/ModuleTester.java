package zlk.util.tester;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.reflect.AccessFlag;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceClassVisitor;

import zlk.common.Type;
import zlk.common.id.Id;
import zlk.common.id.IdMap;
import zlk.core.Builtin;
import zlk.diagnostic.Diagnostic;
import zlk.ir.ast.Module;
import zlk.ir.clcalc.CcModule;
import zlk.ir.idcalc.IcModule;
import zlk.ir.token.Tokenized;
import zlk.phase.clconv.ClosureConverter;
import zlk.phase.codegen.BytecodeGenerator;
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
		CLOSURE_CONV,
		BYTECODE_GEN,
		;

		public boolean includes(CompileLevel other) {
			return this.compareTo(other) >= 0;
		}
	}

	private static final String TARGET_MODULE_NAME = "Main";
	private static final String TARGET_FILE_NAME = "Main.zlk";
	private final CompileLevel compileLevel;
	private final String src;
	private final InMemoryClassLoader classLoader;

	// CompileLevelに応じて用意するもの
	private Module ast = null;
	private Seq<Diagnostic.SyntaxError> parseErrors = null;
	private IcModule module = null;
	private Constraint cint = null;
	private IdMap<Type> types = null;
	private Seq<Diagnostic> patternErrors = null;
	private CcModule clconv = null;
	private final Map<String, ValueTester> functions = new HashMap<>();
	private List<String> generatedClassNames = Collections.emptyList();

	public ModuleTester(String src, CompileLevel level) {
		this.compileLevel = level;
		this.src = "module " + TARGET_MODULE_NAME + "\n" + src;  // TODO: これ要る？
		this.classLoader = new InMemoryClassLoader();

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

		this.types = new IdMap<>();
		Builtin.functions().forEach(fun -> types.put(fun.id(), fun.type()));
		module.types().forEach(union ->
				union.ctors().forEach(ctor ->
					types.put(ctor.id(), Type.fromSeq(Seq.concat(
							ctor.args(),
							Seq.of(new Type.CtorApp(union.id(), union.vars())))))));
		var reconed = TypeReconstructor.recon(result, freshFlex);
		reconed.types().forEach((id, ty) -> types.put(id, ty));
		if(this.compileLevel == CompileLevel.TYPE_RECON) {
			return;
		}

		this.patternErrors = PatternChecker.check(module, reconed.caseTypings());
		if(this.compileLevel == CompileLevel.PATTERN_CHECK) {
			return;
		}
		if(!patternErrors.isEmpty()) {
			throw new IllegalStateException("pattern errors: " + patternErrors.join(", "));
		}

		Seq<Id> builtinIds = Builtin.functions().map(b -> b.id());
		this.clconv = new ClosureConverter(module, types, builtinIds).convert();
		if(this.compileLevel == CompileLevel.CLOSURE_CONV) {
			return;
		}

		record NameAndBytecode(String name, byte[] bytecode) {}
		List<NameAndBytecode> classes = new ArrayList<>();
		new BytecodeGenerator(clconv, types, Builtin.functions(), TARGET_FILE_NAME).compile((name, bytecode) -> classes.add(new NameAndBytecode(name, bytecode)));
		this.generatedClassNames = classes.stream().map(c -> c.name).toList();
		classes.forEach(clz -> {
			DumpOnFailureWatcher.setLastClassDump(clz.name, clz.bytecode);  // TODO: 並列化のためにBeforeEachCallbackでStoreにする
			defineClass(clz.name, clz.bytecode);
		});
		registerFunctions();
	}

	public Module getAst() {
		return ast;
	}

	public Constraint getConstraint() {
		return this.cint;
	}

	public TypeTester getType(String name) {
		Type ty = types.get(Id.intern(TARGET_MODULE_NAME+"."+name));
		return toTypeTester(ty);
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

	/**
	 * バイトコード生成で生成された全クラス名のimmutable viewを返す．
	 * runtime動作に影響しない観測用API．
	 */
	public List<String> getGeneratedClassNames() {
		return generatedClassNames;
	}

	/**
	 * Mainクラスの指定名のpublic staticメソッドを返す．観測用helper．
	 * 引数型が不要な場合は引数なしで検索し，見つからなければ全public staticメソッドから同名を探す．
	 */
	public Method getMainMethod(String name) {
		try {
			Class<?> cls = classLoader.loadClass(TARGET_MODULE_NAME);
			try {
				return cls.getDeclaredMethod(name);
			} catch (NoSuchMethodException _) {
				for (Method m : cls.getDeclaredMethods()) {
					if (m.getName().equals(name)
							&& m.accessFlags().contains(AccessFlag.PUBLIC)
							&& m.accessFlags().contains(AccessFlag.STATIC)) {
						return m;
					}
				}
			}
		} catch (ClassNotFoundException e) {
			// fall through
		}
		throw new IllegalArgumentException("Method not found: " + name);
	}

	private TypeTester toTypeTester(Type ty) {
		return new TypeTester(
				ty,
				Id.intern(TARGET_MODULE_NAME),
				module.types().map(d -> d.id()).fold(IdMap.folder(i -> i, i -> new Type.CtorApp(i, Seq.of()))));
	}

	private void defineClass(String className, byte[] bytecode) {
		try {
			classLoader.define(className, bytecode);
		} catch (Exception e) {
			throw new RuntimeException("Failed to load class: " + className, e);
		}
	}

	private void registerFunctions() {
		try {
			Class<?> cls = classLoader.loadClass(TARGET_MODULE_NAME);
			for (Method method : cls.getDeclaredMethods()) {
				if(method.accessFlags().contains(AccessFlag.PUBLIC)
						&& method.accessFlags().contains(AccessFlag.STATIC)) {
					String name = method.getName();
					functions.put(name, ValueTester.of(method));
				}
			}
		} catch (ClassNotFoundException e) {
			throw new RuntimeException("Failed to load class: " + TARGET_MODULE_NAME, e);
		}
	}

	public ValueTester getValue(String name) {
		if (!functions.containsKey(name)) {
			throw new IllegalArgumentException("Function not found: " + name);
		}
		return functions.get(name);
	}

	public void dumpClass(byte[] classBytes, OutputStream out) {
		PrintWriter pw = new PrintWriter(out);
		TraceClassVisitor tcv = new TraceClassVisitor(null, new Textifier(), pw);

		ClassReader cr = new ClassReader(classBytes);
		cr.accept(tcv, 0);
	}

}

class InMemoryClassLoader extends ClassLoader {
	public Class<?> define(String name, byte[] bytecode) {
		return defineClass(name, bytecode, 0, bytecode.length);
	}
}
