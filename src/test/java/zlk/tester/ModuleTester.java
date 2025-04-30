package zlk.tester;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import zlk.ast.Module;
import zlk.bytecodegen.BytecodeGenerator;
import zlk.clcalc.CcModule;
import zlk.clconv.ClosureConveter;
import zlk.common.Type;
import zlk.common.id.IdList;
import zlk.common.id.IdMap;
import zlk.core.Builtin;
import zlk.idcalc.IcModule;
import zlk.nameeval.NameEvaluator;
import zlk.parser.Lexer;
import zlk.parser.Parser;
import zlk.recon.ConstraintExtractor;
import zlk.recon.TypeReconstructor;
import zlk.recon.constraint.Constraint;
import zlk.typecheck.TypeChecker;

public class ModuleTester {
	private static final String TARGET_MODULE_NAME = "Main";
	private final String src;
	private final InMemoryClassLoader classLoader;
	private final Map<String, ValueTester> functions = new HashMap<>();

	public ModuleTester(String src) {
		this.src = "module " + TARGET_MODULE_NAME + "\n" + src;
		this.classLoader = new InMemoryClassLoader();

		// TODO コンパイラのデザイン
		Module ast = new Parser(new Lexer(TARGET_MODULE_NAME + ".zlk", this.src)).parse();
		IdList builtinIds = Builtin.functions().stream().map(b -> b.id()).collect(IdList.collector());
		IcModule idcalc = new NameEvaluator(ast, Builtin.functions()).eval();
		Constraint cint = ConstraintExtractor.extract(idcalc);
		IdMap<Type> types = new TypeReconstructor().run(cint);

		// TODO ↓これreconする前じゃね？
		idcalc.types().forEach(union ->
			union.ctors().forEach(ctor ->
				types.put(ctor.id(), Type.arrow(ctor.args(), new Type.Atom(union.id())))));
		Builtin.functions().forEach(b -> types.put(b.id(), b.type()));

		new TypeChecker(types).check(idcalc);
		CcModule clconv = new ClosureConveter(idcalc, types, builtinIds).convert();
		new BytecodeGenerator(clconv, types, Builtin.functions()).compile(this::addClass);
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