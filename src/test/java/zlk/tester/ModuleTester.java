package zlk.tester;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import zlk.ast.Module;
import zlk.bytecodegen.BytecodeGenerator;
import zlk.clcalc.CcModule;
import zlk.clconv.ClosureConveter;
import zlk.common.Type;
import zlk.common.id.Id;
import zlk.common.id.IdList;
import zlk.common.id.IdMap;
import zlk.core.Builtin;
import zlk.idcalc.IcModule;
import zlk.nameeval.NameEvaluator;
import zlk.parser.Lexer;
import zlk.parser.Parser;
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
	private final CompileLevel compileLevel;
	private final String src;
	private final InMemoryClassLoader classLoader;

	// CompileLevelに応じて用意するもの
	private Module ast = null;
	private IcModule module = null;
	private Constraint cint = null;
	private IdMap<Type> types = null;
	private CcModule clconv = null;
	private final Map<String, ValueTester> functions = new HashMap<>();

	public ModuleTester(String src, CompileLevel level) {
		this.compileLevel = level;
		this.src = "module " + TARGET_MODULE_NAME + "\n" + src;  // TODO: これ要る？
		this.classLoader = new InMemoryClassLoader();

		this.ast = new Parser(new Lexer(TARGET_MODULE_NAME + ".zlk", this.src)).parse();
		if(this.compileLevel == CompileLevel.PARSE) {
			return;
		}

		this.module = new NameEvaluator(ast).eval();
		if(this.compileLevel == CompileLevel.NAME_EVAL) {
			return;
		}

		FreshFlex freshFlex = new FreshFlex();
		this.cint = ConstraintExtractor.extract(module, freshFlex);
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
		if(this.compileLevel == CompileLevel.TYPE_RECON) {
			return;
		}

		IdList builtinIds = Builtin.functions().stream().map(b -> b.id())
				.collect(IdList.collector());
		this.clconv = new ClosureConveter(module, types, builtinIds).convert();
		if(this.compileLevel == CompileLevel.CLOSURE_CONV) {
			return;
		}

		new BytecodeGenerator(clconv, types, Builtin.functions()).compile(this::addClass);
	}

	public Constraint getConstraint() {
		return this.cint;
	}

	public TypeTester getType(String name) {
		Type ty = types.get(Id.fromCanonicalName(TARGET_MODULE_NAME+"."+name));
		return new TypeTester(ty, Id.fromCanonicalName(TARGET_MODULE_NAME), module.types().stream().map(d -> d.id()).collect(IdMap.collector(i -> i, i -> new Type.CtorApp(i, List.of()))));
	}

	public void addClass(String className, byte[] bytecode) {
		try {
			Class<?> cls = classLoader.define(className, bytecode);
			for (Method method : cls.getDeclaredMethods()) {
				String name = method.getName();
				functions.put(name, ValueTester.of(method));
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
}

class InMemoryClassLoader extends ClassLoader {
	public Class<?> define(String name, byte[] bytecode) {
		return defineClass(name, bytecode, 0, bytecode.length);
	}
}