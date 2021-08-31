package zlk;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.TraceClassVisitor;

import zlk.ast.Module;
import zlk.bytecodegen.BytecodeGenerator;
import zlk.idcalc.IcModule;
import zlk.nameeval.IdGenerator;
import zlk.nameeval.NameEvaluator;
import zlk.parser.Lexer;
import zlk.parser.Parser;
import zlk.typecheck.TypeChecker;

public class Main {
	public static void main( String[] args ) throws IOException {
		String name = "HelloMyLang";
		String src =
				"""
				module HelloMyLang

				fact n : I32 -> I32 =
					if isZero n then 1 else mul n (fact (sub n 1));

				ans : I32 =
					fact 10;
				""";

		Module ast = new Parser(new Lexer(name, src)).parse();

		System.out.println("-- AST --");
		System.out.println(ast.mkString());

		IdGenerator fresh = new IdGenerator();
		IcModule idcalc = new NameEvaluator(fresh).eval(ast);

		System.out.println("-- ID CALC --");
		System.out.println(idcalc.mkString());

		System.out.println("-- TYPE CHECK --");
		idcalc.decls().forEach(
				decl -> System.out.println(decl.fun().name() + " : " + TypeChecker.check(decl).mkString()));
		System.out.println();

		System.out.println("-- BYTECODE --");
		byte[] bin = new BytecodeGenerator().compile(idcalc);
		new ClassReader(bin).accept(
				new TraceClassVisitor(
						new PrintWriter(System.out)), 0);

		Files.write(Paths.get(name + ".class"), bin);
	}
}
