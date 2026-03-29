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

import zlk.ast.Module;
import zlk.bytecodegen.BytecodeGenerator;
import zlk.clcalc.CcModule;
import zlk.clconv.ClosureConverter;
import zlk.common.Type;
import zlk.common.id.Id;
import zlk.common.id.IdList;
import zlk.common.id.IdMap;
import zlk.core.Builtin;
import zlk.idcalc.ExpOrPattern;
import zlk.idcalc.IcModule;
import zlk.nameeval.NameEvaluator;
import zlk.parser.Tokenized;
import zlk.recon.ConstraintExtractor;
import zlk.recon.FreshFlex;
import zlk.recon.TypeError;
import zlk.recon.TypeReconstructor;
import zlk.recon.constraint.Constraint;
import zlk.util.Result;

public class ModuleTester {

	public enum CompileLevel {
		PARSE,
		NAME_EVAL,
		TYPE_CINT,
		TYPE_RECON,
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
	private IcModule module = null;
	private Constraint cint = null;
	private IdentityHashMap<ExpOrPattern, Type> callSiteTypes = null;
	private IdMap<Type> types = null;
	private CcModule clconv = null;
	private final Map<String, ValueTester> functions = new HashMap<>();

	public ModuleTester(String src, CompileLevel level) {
		this.compileLevel = level;
		this.src = "module " + TARGET_MODULE_NAME + "\n" + src;  // TODO: これ要る？
		this.classLoader = new InMemoryClassLoader();

		Tokenized tokens = new zlk.parser.Lexer(TARGET_FILE_NAME, this.src).lex();
		this.ast = zlk.parser.Parser.parse(tokens);

		if(this.compileLevel == CompileLevel.PARSE) {
			return;
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
					types.put(ctor.id(), Type.arrow(ctor.args(), new Type.CtorApp(union.id())))));
		Result<List<TypeError>, IdMap<Type>> reconResult = TypeReconstructor.recon(cint, freshFlex);
		reconResult.unwrap().forEach((id, ty) -> types.put(id, ty));
		callSiteTypes = result.resolvedNodeTypes();
		if(this.compileLevel == CompileLevel.TYPE_RECON) {
			return;
		}

		IdList builtinIds = Builtin.functions().stream().map(b -> b.id())
				.collect(IdList.collector());
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

	public Constraint getConstraint() {
		return this.cint;
	}

	public TypeTester getType(String name) {
		Type ty = types.get(Id.fromCanonicalName(TARGET_MODULE_NAME+"."+name));
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

	private TypeTester toTypeTester(Type ty) {
		return new TypeTester(
				ty,
				Id.fromCanonicalName(TARGET_MODULE_NAME),
				module.types().stream().map(d -> d.id()).collect(IdMap.collector(i -> i, i -> new Type.CtorApp(i, List.of()))));
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
}

class InMemoryClassLoader extends ClassLoader {
	public Class<?> define(String name, byte[] bytecode) {
		return defineClass(name, bytecode, 0, bytecode.length);
	}
}
