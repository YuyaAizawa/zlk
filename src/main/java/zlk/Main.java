package zlk;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.TraceClassVisitor;

import zlk.ast.Module;
import zlk.bytecodegen.BytecodeGenerator;
import zlk.clcalc.CcModule;
import zlk.clconv.ClosureConveter;
import zlk.common.id.IdList;
import zlk.common.id.IdMap;
import zlk.common.type.TyAtom;
import zlk.common.type.Type;
import zlk.core.Builtin;
import zlk.idcalc.IcModule;
import zlk.nameeval.NameEvaluator;
import zlk.parser.Lexer;
import zlk.parser.Parser;
import zlk.recon.ConstraintExtractor;
import zlk.recon.TypeReconstructor;
import zlk.recon.constraint.Constraint;
import zlk.typecheck.TypeChecker;

public class Main {

	public static Class<?> clazz;

	public static void main( String[] args ) throws IOException, ClassNotFoundException, NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		String name = "HelloMyLang";
		String src =
				"""
				module HelloMyLang

				type IntList = Nil | Cons I32 IntList

				sq a  =
				  let
				    pow b c =
				      if isZero c
				      then 1
				      else mul b (pow b (sub c 1))
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

		System.out.println("-- AST --");
		Module ast = new Parser(new Lexer(name, src)).parse();
		ast.pp(System.out);
		System.out.println();

		System.out.println("-- NAME EVAL --");
		IdList builtinIds = Builtin.builtins().stream().map(b -> b.id()).collect(IdList.collector());
		NameEvaluator ne = new NameEvaluator(ast, Builtin.builtins());
		IcModule idcalc = ne.eval();
		idcalc.pp(System.out);
		System.out.println();

//		System.out.println("-- UNCURRY --");
//		idcalc = null;
//		System.out.println();

		System.out.println("-- EXTRACT CONSTRAINS --");
		Constraint cint = ConstraintExtractor.extract(idcalc);
		System.out.println(cint);
		System.out.println();

		System.out.println("-- TYPE RECONSTRUCTION --");
		TypeReconstructor tr = new TypeReconstructor();
		IdMap<Type> types = tr.run(cint);
		System.out.println(types);
		System.out.println();

		idcalc.types().forEach(union -> union.ctors().forEach(ctor ->
			types.put(ctor.id(), Type.arrow(ctor.args(), new TyAtom(union.id())))));
		Builtin.builtins().forEach(b -> types.put(b.id(), b.type()));

		System.out.println("-- TYPE CHECK --"); // TODO remove
		TypeChecker typeChecker = new TypeChecker(types);
		typeChecker.check(idcalc);
		System.out.println();

		System.out.println("-- CL CONV --");
		CcModule clconv = new ClosureConveter(idcalc, types, builtinIds).convert();
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
					classBins.put(name_, bin);
					Files.write(Paths.get(name_), bin);
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			};

		new BytecodeGenerator(clconv, types, Builtin.builtins()).compile(fileWriter);

		System.out.println();
		System.out.println("-- EXECUTE --");

		clazz = Class.forName(name, true, new ClassLoader() {
			@Override
			protected java.lang.Class<?> findClass(String str) throws ClassNotFoundException {
				byte[] bin = classBins.get(str);
				if(bin == null) {
					throw new ClassNotFoundException(str);
				}
				return defineClass(str, bin, 0, bin.length);
			}
		});

		invoke("ans1", "sq 42 = ");
		invoke("ans2", "fact 10 = ");
		invoke("ans3", "make_adder 3 4 5 = ");
	}

	private static void invoke(String target, String description) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
		System.out.println(description + clazz.getDeclaredMethod(target).invoke(null));
	}
}
