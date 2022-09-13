package zlk;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.TraceClassVisitor;

import zlk.ast.Module;
import zlk.bytecodegen.BytecodeGenerator;
import zlk.clcalc.CcModule;
import zlk.clconv.ClosureConveter;
import zlk.common.IdGenerator;
import zlk.common.IdMap;
import zlk.core.Builtin;
import zlk.idcalc.IcModule;
import zlk.nameeval.NameEvaluator;
import zlk.parser.Lexer;
import zlk.parser.Parser;
import zlk.typecheck.TypeChecker;

public class Main {

	public static Class<?> clazz;

	public static void main( String[] args ) throws IOException, ClassNotFoundException, NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		String name = "HelloMyLang";
		String src =
				"""
				module HelloMyLang

				sq a : I32 -> I32 =
				  let
				    pow b c : I32 -> I32 -> I32 =
				      if isZero c
				      then 1
				      else mul b (pow b (sub c 1))
				  in
				    pow a 2

				fact n : I32 -> I32 =
				  if isZero n then
				    1
				  else
				    let
				      one : I32 = 1
				      nn : I32 = sub n one
				    in
				      mul n (fact nn)

				make_adder x : I32 -> I32 -> I32 =
				  let adder y : I32 -> I32 = add x y in
				  adder

				ans1 : I32 =
				  sq 42

				ans2 : I32 =
				  fact 10

				ans3 : I32 =
				  (make_adder 3) 7
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
		IdMap<Builtin> builtins = Builtin.builtins().stream().collect(IdMap.collector(b -> ne.registerBuiltin(b), b -> b));
		IcModule idcalc = ne.eval(ast);
		idcalc.pp(System.out);
		System.out.println();

		System.out.println("-- TYPE CHECK --");
		idcalc.decls().forEach(
				decl -> System.out.println(decl.id().name() + " : " + TypeChecker.check(decl).toString()));
		System.out.println();

		System.out.println("-- CL CONV --");
		CcModule clconv = new ClosureConveter(idcalc, builtins, fresh).convert();
		clconv.pp(System.out);
		System.out.println();

		System.out.println("-- BYTECODE --");
		byte[] bin = new BytecodeGenerator(clconv, builtins).compile();
		new ClassReader(bin).accept(
				new TraceClassVisitor(
						new PrintWriter(System.out)), 0);

		Files.write(Paths.get(name + ".class"), bin);
		System.out.println();

		System.out.println("-- EXECUTE --");

		clazz = Class.forName(name, true, new ClassLoader() {
			@Override
			protected java.lang.Class<?> findClass(String str) throws ClassNotFoundException {
				return defineClass(name, bin, 0, bin.length);
			}
		});

		invoke("ans1", "sq 42 = ");
		invoke("ans2", "fact 10 = ");
		invoke("ans3", "(make_adder 3) 7 = ");
	}

	private static void invoke(String target, String description) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
		System.out.println(description + clazz.getDeclaredMethod(target).invoke(null));
	}
}
