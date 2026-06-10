package zlk.tester;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.reflect.AccessFlag;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceClassVisitor;

import zlk.ast.Decl;
import zlk.ast.Exp;
import zlk.ast.Module;
import zlk.bytecodegen.BytecodeGenerator;
import zlk.clcalc.CcModule;
import zlk.clconv.ClosureConverter;
import zlk.common.LocationHolder;
import zlk.common.Type;
import zlk.common.id.Id;
import zlk.common.id.IdMap;
import zlk.core.Builtin;
import zlk.idcalc.ExpOrPattern;
import zlk.idcalc.IcModule;
import zlk.nameeval.NameEvaluator;
import zlk.parser.Tokenized;
import zlk.patterncheck.PatternChecker;
import zlk.patterncheck.PcError;
import zlk.recon.ConstraintExtractor;
import zlk.recon.FreshFlex;
import zlk.recon.TypeError;
import zlk.recon.TypeReconstructor;
import zlk.recon.constraint.Constraint;
import zlk.util.Result;
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
	private Seq<LocationHolder> parseErrors = null;
	private IcModule module = null;
	private Constraint cint = null;
	private IdentityHashMap<ExpOrPattern, Type> callSiteTypes = null;
	private IdMap<Type> types = null;
	private Seq<PcError> patternErrors = null;
	private CcModule clconv = null;
	private final Map<String, ValueTester> functions = new HashMap<>();

	public ModuleTester(String src, CompileLevel level) {
		this.compileLevel = level;
		this.src = "module " + TARGET_MODULE_NAME + "\n" + src;  // TODO: これ要る？
		this.classLoader = new InMemoryClassLoader();

		Tokenized tokens = new zlk.parser.Lexer(TARGET_FILE_NAME, this.src).lex();
		this.ast = zlk.parser.Parser.parse(tokens);
		this.parseErrors = collectParseErrors(ast);

		if(this.compileLevel == CompileLevel.PARSE) {
			return;
		}
		if(!parseErrors.isEmpty()) {
			throw new IllegalStateException("parse errors in line "
					+ parseErrors.map(exp -> exp.loc().startLine()).join(", "));
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
					types.put(ctor.id(), Type.fromSeq(Seq.concat(ctor.args(), Seq.of(new Type.CtorApp(union.id())))))));
		Result<Seq<TypeError>, IdMap<Type>> reconResult = TypeReconstructor.recon(cint, freshFlex);
		reconResult.unwrap().forEach((id, ty) -> types.put(id, ty));
		callSiteTypes = result.resolvedNodeTypes();
		if(this.compileLevel == CompileLevel.TYPE_RECON) {
			return;
		}

		this.patternErrors = PatternChecker.check(module);
		if(this.compileLevel == CompileLevel.PATTERN_CHECK) {
			return;
		}

		Seq<Id> builtinIds = Builtin.functions().map(b -> b.id());
		this.clconv = new ClosureConverter(module, types, callSiteTypes, builtinIds).convert();
		if(this.compileLevel == CompileLevel.CLOSURE_CONV) {
			return;
		}

		record NameAndBytecode(String name, byte[] bytecode) {}
		List<NameAndBytecode> classes = new ArrayList<>();
		new BytecodeGenerator(clconv, types, Builtin.functions(), TARGET_FILE_NAME).compile((name, bytecode) -> classes.add(new NameAndBytecode(name, bytecode)));
		classes.forEach(clz -> {
			DumpOnFailureWatcher.setLastClassDump(clz.name, clz.bytecode);  // TODO: 並列化のためにBeforeEachCallbackでStoreにする
			addClass(clz.name, clz.bytecode);
		});
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

	public TypeTester getCallSiteType(ExpOrPattern node) {
		Type ty = callSiteTypes.get(node);
		if(ty == null) {
			throw new IllegalArgumentException("Type not found for node: " + node);
		}
		return toTypeTester(ty);
	}

	public IcModule getIdcalcModule() {
		return module;
	}

	// 当面は行数だけ使うので使わない
	public Seq<LocationHolder> getParseErrors() {
		return parseErrors;
	}
	public IntSeq getParseErrorStartLines() {
		return parseErrors.mapToInt(err -> err.loc().startLine() - 1);  // module Mainの分を引く
	}

	public Seq<PcError> getPatternErrors() {
		return patternErrors;
	}

	private TypeTester toTypeTester(Type ty) {
		return new TypeTester(
				ty,
				Id.intern(TARGET_MODULE_NAME),
				module.types().map(d -> d.id()).fold(IdMap.folder(i -> i, i -> new Type.CtorApp(i, Seq.of()))));
	}

	public void addClass(String className, byte[] bytecode) {
		try {
			Class<?> cls = classLoader.define(className, bytecode);
			for (Method method : cls.getDeclaredMethods()) {
				if(method.accessFlags().contains(AccessFlag.PUBLIC)) {
					String name = method.getName();
					functions.put(name, ValueTester.of(method));
				}
			}
		} catch (Exception e) {
			throw new RuntimeException("Failed to load class: " + className, e);
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

	private static Seq<LocationHolder> collectParseErrors(Module module) {
		SeqBuffer<LocationHolder> errors = new SeqBuffer<>();
		for(Decl decl : module.decls()) {
			switch(decl) {
			case Decl.ValDecl valDecl -> collectParseErrors(valDecl.body(), errors);
			case Decl.ValErr err -> errors.add(err);
			case Decl.TypeErr err -> errors.add(err);
			case Decl.TypeDecl _ -> {}
			}
		}
		return errors.toSeq();
	}

	private static void collectParseErrors(Exp exp, SeqBuffer<LocationHolder> errors) {
		switch(exp) {
		case Exp.Cnst _, Exp.Var _ -> {}
		case Exp.Err err -> errors.add(err);
		case Exp.Lamb(_, Exp body, _) -> collectParseErrors(body, errors);
		case Exp.App(Seq<Exp> exps, _) -> exps.forEach(e -> collectParseErrors(e, errors));
		case Exp.If(Exp cond, Exp thenExp, Exp elseExp, _) -> {
			collectParseErrors(cond, errors);
			collectParseErrors(thenExp, errors);
			collectParseErrors(elseExp, errors);
		}
		case Exp.Let(Seq<Decl.Value> decls, Exp body, _) -> {
			decls.forEach(decl -> {
				switch(decl) {
				case Decl.ValDecl valDecl -> collectParseErrors(valDecl.body(), errors);
				case Decl.ValErr err -> errors.add(err);
				}
			});
			collectParseErrors(body, errors);
		}
		case Exp.Case(Exp scrutinee, Seq<zlk.ast.CaseBranch> branches, _) -> {
			collectParseErrors(scrutinee, errors);
			branches.forEach(branch -> collectParseErrors(branch.body(), errors));
		}
		}
	}
}

class InMemoryClassLoader extends ClassLoader {
	public Class<?> define(String name, byte[] bytecode) {
		return defineClass(name, bytecode, 0, bytecode.length);
	}
}
