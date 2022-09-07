package zlk;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.stream.Collectors;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.TraceClassVisitor;

import zlk.ast.Module;
import zlk.bytecodegen.BytecodeGenerator;
import zlk.core.Builtin;
import zlk.idcalc.IcModule;
import zlk.idcalc.IdInfo;
import zlk.nameeval.IdGenerator;
import zlk.nameeval.NameEvaluator;
import zlk.parser.Lexer;
import zlk.parser.Parser;
import zlk.typecheck.TypeChecker;

public class Main {
	public static void main( String[] args ) throws IOException, ClassNotFoundException, NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		String name = "HelloMyLang";
		String src =
				"""
				module HelloMyLang

				fact n : I32 -> I32 =
				  if isZero n
				  then
				  1
				  else
				  let
				    one : I32 = 1
				    nn : I32 = sub n one
				  in
				    mul n (fact nn)

				ans : I32 =
				  fact 10
				""";

		System.out.println("-- SOURCE --");
		System.out.println(src);
		System.out.println();

		System.out.println("-- AST --");
		Module ast = new Parser(new Lexer(name, src)).parse();
		ast.pp(System.out);
		System.out.println();

		System.out.println("-- ID CALC --");
		IdGenerator fresh = new IdGenerator();
		NameEvaluator ne = new NameEvaluator(fresh);
		Map<IdInfo, Builtin> builtins = Builtin.builtins().stream().collect(Collectors.toMap(b -> ne.registerBuiltin(b), b -> b));
		IcModule idcalc = ne.eval(ast);
		idcalc.pp(System.out);
		System.out.println();

		System.out.println("-- TYPE CHECK --");
		idcalc.decls().forEach(
				decl -> System.out.println(decl.id().name() + " : " + TypeChecker.check(decl).toString()));
		System.out.println();

		System.out.println("-- BYTECODE --");
		byte[] bin = new BytecodeGenerator(builtins).compile(idcalc);
		new ClassReader(bin).accept(
				new TraceClassVisitor(
						new PrintWriter(System.out)), 0);

		Files.write(Paths.get(name + ".class"), bin);
		System.out.println();

		System.out.println("-- EXECUTE --");

		Class<?> clazz = Class.forName(name, true, new ClassLoader() {
			@Override
			protected java.lang.Class<?> findClass(String str) throws ClassNotFoundException {
				return defineClass(name, bin, 0, bin.length);
			}
		});

		System.out.println(clazz.getDeclaredMethod("ans").invoke(null));
	}
}
