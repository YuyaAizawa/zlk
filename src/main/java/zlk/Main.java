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
import zlk.nameeval.NameEvaluator;
import zlk.parser.Lexer;
import zlk.parser.Parser;

public class Main {
	public static void main( String[] args ) throws IOException {
		String name = "HelloMyLang";
		String src =
				"""
				module HelloMyLang

				add3 i j k : I32 -> I32 -> I32 -> I32 =
					add (add i j) k;

				ans : I32 =
					add3 13 14 15;
				""";

		Module ast = new Parser(new Lexer(name, src)).parse();

		System.out.println();
		System.out.println(ast.mkString());

		IcModule idcalc = new NameEvaluator().eval(ast);

		System.out.println();
		System.out.println(idcalc.mkString());

		byte[] bin = new BytecodeGenerator().compile(idcalc);

		new ClassReader(bin).accept(
				new TraceClassVisitor(
						new PrintWriter(System.out)), 0);

		Files.write(Paths.get(name + ".class"), bin);
	}
}
