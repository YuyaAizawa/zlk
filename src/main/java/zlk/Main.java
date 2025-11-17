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
import zlk.common.Type;
import zlk.common.id.IdList;
import zlk.common.id.IdMap;
import zlk.core.Builtin;
import zlk.idcalc.IcModule;
import zlk.nameeval.NameEvaluator;
import zlk.parser.Lexer;
import zlk.parser.Parser;
import zlk.recon.ConstraintExtractor;
import zlk.recon.FreshFlex;
import zlk.recon.LetDependencyExtractor;
import zlk.recon.TypeReconstructor;
import zlk.recon.constraint.Constraint;
import zlk.typecheck.TypeChecker;

public class Main {

        public static Class<?> clazz;

        public static void main(String[] args) throws IOException, ClassNotFoundException, NoSuchMethodException, SecurityException,
                        IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchFieldException {
                String name = "HelloMyLang.zlk";
                String src =
                                """
                                module HelloMyLang

                                type List a =
                                | Nil
                                | Cons a (List a)

                                type Pair a b = Pair_ a b

                                intList = Cons 1 Nil
                                boolList = Cons True Nil
                                pair x y = Pair_ y x

                                """;

                System.out.println("-- SOURCE --");
                System.out.println(src);
                System.out.println();

                System.out.println("-- AST --");
                Module ast = new Parser(new Lexer(name, src)).parse();
                ast.pp(System.out);
                System.out.println();

                System.out.println("-- NAME EVAL --");
                IdList builtinIds = Builtin.functions().stream().map(b -> b.id()).collect(IdList.collector());
                NameEvaluator ne = new NameEvaluator(ast);
                IcModule idcalc = ne.eval();
                idcalc.pp(System.out);
                System.out.println();

                System.out.println("-- LET DEPENDENCY EXTRACTION --");
                IdMap<IdList> letDependers = LetDependencyExtractor.extract(idcalc);
                letDependers.pp(System.out);
                System.out.println();

                System.out.println("-- CONSTRAIN EXTRACTION --");
                FreshFlex freshFlex = new FreshFlex();
                Constraint cint = ConstraintExtractor.extract(idcalc, letDependers, freshFlex);
                System.out.println(cint.buildString());
                System.out.println();

                System.out.println("-- TYPE RECONSTRUCTION --");
                IdMap<Type> types = TypeReconstructor.recon(cint, freshFlex).unwrap();
                System.out.println(types.buildString());
                System.out.println();

                idcalc.types().forEach(union -> union.ctors().forEach(ctor ->
                                types.put(ctor.id(), Type.arrow(ctor.args(), new Type.CtorApp(union.id())))));
                Builtin.functions().forEach(b -> types.put(b.id(), b.type()));

                System.out.println("-- TYPE CHECK --");  // TODO remove
                TypeChecker typeChecker = new TypeChecker(types);
                typeChecker.check(idcalc);
                System.out.println(types);
                System.out.println();

                System.out.println("-- CL CONV --");
                CcModule clconv = new ClosureConveter(idcalc, types, builtinIds, letDependers).convert();
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

                new BytecodeGenerator(clconv, types, Builtin.functions()).compile(fileWriter);
        }
}
