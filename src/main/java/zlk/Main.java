package zlk;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.TraceClassVisitor;

import zlk.ast.CompileUnit;
import zlk.bytecodegen.BytecodeGenerator;
import zlk.parser.Lexer;
import zlk.parser.Parser;

public class Main {
	public static void main( String[] args ) throws IOException {
		String name = "HelloMyLang";
		String src =
				"""
				module HelloMyLang

				ten : I32 =
						10;

				ans : I32 =
						add ten (add (add ten ten) 12);
				""";

		CompileUnit ast = new Parser(new Lexer(src)).parse();
		byte[] bin = new BytecodeGenerator().compile(ast);

		new ClassReader(bin).accept(
				new TraceClassVisitor(
						new PrintWriter(System.out)), 0);

		Files.write(Paths.get(name + ".class"), bin);
	}
}
