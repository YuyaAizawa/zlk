package zlk;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.TraceClassVisitor;

import zlk.ast.Module;
import zlk.bytecodegen.BytecodeGenerator;
import zlk.clcalc.CcModule;
import zlk.clconv.ClosureConverter;
import zlk.common.Type;
import zlk.common.id.IdList;
import zlk.common.id.IdMap;
import zlk.core.Builtin;
import zlk.idcalc.ExpOrPattern;
import zlk.idcalc.IcModule;
import zlk.nameeval.NameEvaluator;
import zlk.parser.Lexer;
import zlk.parser.Parser;
import zlk.parser.Tokenized;
import zlk.recon.ConstraintExtractor;
import zlk.recon.FreshFlex;
import zlk.recon.TypeReconstructor;
import zlk.recon.constraint.Constraint;

public class Main {

	public static Class<?> clazz;

	public static void main( String[] args ) throws IOException, ClassNotFoundException, NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchFieldException {
		String name = "HelloMyLang.zlk";
		String src =
				"""
				module HelloMyLang

				type List a =
				  | Nil
				  | Cons a (List a)

				sq a =
				  let
				    pow b c =
				      if isZero c then
				        1
				      else
				        mul b (pow b (sub c 1))
				  in
				    pow a 2

				fact n =
				  if isZero n then
				    1
				  else
				    let
				      one = 1
				      nn = sub n one
				    in
				      mul n (fact nn)

				make_adder x =
				  let
				    adder y =
				      let
				        adder2 z = add (add x y) z
				      in
				        adder2
				  in
				    adder

				sum list =
				  case list of
				    Nil -> 0
				    Cons hd tl -> add hd (sum tl)

				ans1 =
				  sq 42

				ans2 =
				  sum (Cons 3 (Cons 2 (Cons 1 Nil)))

				ans3 =
				  make_adder 3 4 5
				""";

		System.out.println("-- SOURCE --");
		System.out.println(src);
		System.out.println();

		System.out.println("-- TOKENS --");
		Tokenized tokens = new Lexer(name, src).lex();
		tokens.forEach(token -> System.out.println(token));
		System.out.println();

		System.out.println("-- AST --");
		Module ast = Parser.parse(tokens);
		System.out.println(ast.buildString());
		System.out.println();

		System.out.println("-- NAME EVAL --");
		IdList builtinIds = Builtin.functions().stream().map(b -> b.id()).collect(IdList.collector());
		NameEvaluator ne = new NameEvaluator(ast);
		IcModule idcalc = ne.eval();
		System.out.println(idcalc.buildString());
		System.out.println();

		System.out.println("-- CONSTRAIN EXTRACTION --");
		FreshFlex freshFlex = new FreshFlex();
		ConstraintExtractor.Result extractResult = ConstraintExtractor.extract(idcalc, freshFlex);
		Constraint cint = extractResult.constraint();
		System.out.println(cint.buildString());
		System.out.println();

		System.out.println("-- TYPE RECONSTRUCTION --");
		IdMap<Type> types = TypeReconstructor.recon(cint, freshFlex).unwrap();
		System.out.println(types.buildString());
		System.out.println();

		IdentityHashMap<ExpOrPattern, Type> nodeTypes = extractResult.resolvedNodeTypes();

		idcalc.types().forEach(union -> union.ctors().forEach(ctor ->
			types.put(ctor.id(), Type.arrow(ctor.args().toList(), new Type.CtorApp(union.id(), union.vars().toList())))));
		Builtin.functions().forEach(b -> types.put(b.id(), b.type()));

		System.out.println("-- CL CONV --");
		CcModule clconv = new ClosureConverter(idcalc, types, nodeTypes, builtinIds).convert();
		clconv.pp(System.out);
		System.out.println();

		System.out.println("-- BYTECODE GEN --");

		Map<String, byte[]> classBins = new HashMap<>();
		BiConsumer<String, byte[]> fileWriter =
			(name_, bin) -> {
				try {
					new ClassReader(bin).accept(
							new TraceClassVisitor(
									new PrintWriter(System.out)), 0);
					classBins.put(name_.split("\\.")[0], bin);
					Files.write(Paths.get(name_ + ".class"), bin);
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			};

		new BytecodeGenerator(clconv, types, Builtin.functions(), name).compile(fileWriter);

		System.out.println();
		System.out.println("-- EXECUTE --");

		ClassLoader cl =
				new ClassLoader() {
					@Override
					protected java.lang.Class<?> findClass(String str) throws ClassNotFoundException {
						byte[] bin = classBins.get(str);
						if(bin == null) {
							throw new ClassNotFoundException(str);
						}
						return defineClass(str, bin, 0, bin.length);
					}
				};
		clazz = Class.forName(name.split("\\.")[0], true, cl);

		invoke("ans1", "sq 42 = ");
		invoke("ans2", "sum (Cons 3 (Cons 2 (Cons 1 Nil))) = ");
		invoke("ans3", "make_adder 3 4 5 = ");
	}

	private static void invoke(String target, String description) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
		System.out.println(description + clazz.getDeclaredMethod(target).invoke(null));
	}
}
