package zlk.test.feature.function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import zlk.compiler.CompilationOptions;
import zlk.compiler.CompilationOptions.Key;
import zlk.compiler.driver.Driver;
import zlk.util.fixture.CompilationFixture;

public class OperandStackAllocationFeatureTest {
	private static final String UNNAMED_INTERMEDIATES =
			"calculate value = add (sub value 1) (mul value 2)\n";

	@Test
	void usesOperandStackForUnnamedSingleUseValuesByDefault() {
		CompilationFixture module = CompilationFixture.compileSucceeded(UNNAMED_INTERMEDIATES);

		assertEquals(0, localVariableAccesses(module, "calculate", 1));
	}

	@Test
	void usesOperandStackForUnnamedSingleUseValuesWhenEnabled() {
		CompilationFixture module = CompilationFixture.compileSucceeded(
				UNNAMED_INTERMEDIATES,
				CompilationOptions.DEFAULT.enable(Key.OPT_USE_OPERAND_STACK));

		assertEquals(0, localVariableAccesses(module, "calculate", 1));
	}

	@Test
	void usesLocalVariableSlotsForUnnamedSingleUseValuesWhenDisabled() {
		CompilationFixture module = CompilationFixture.compileSucceeded(
				UNNAMED_INTERMEDIATES,
				CompilationOptions.DEFAULT.disable(Key.OPT_USE_OPERAND_STACK));

		assertTrue(localVariableAccesses(module, "calculate", 1) > 0);
	}

	@Test
	void bothAllocationModesHaveTheSameRuntimeSemantics() {
		CompilationFixture enabled = CompilationFixture.compileSucceeded(
				UNNAMED_INTERMEDIATES,
				CompilationOptions.DEFAULT.enable(Key.OPT_USE_OPERAND_STACK));
		CompilationFixture disabled = CompilationFixture.compileSucceeded(
				UNNAMED_INTERMEDIATES,
				CompilationOptions.DEFAULT.disable(Key.OPT_USE_OPERAND_STACK));

		assertEquals(8, enabled.value("calculate", 3));
		assertEquals(8, disabled.value("calculate", 3));
	}

	@Test
	void namedValuesUseLocalVariableSlotsWhenEnabled() {
		CompilationFixture module = CompilationFixture.compileSucceeded(
				"""
				calculate a b =
				  let
				    sum = add a b
				  in
				    mul sum sum
				""",
				CompilationOptions.DEFAULT.enable(Key.OPT_USE_OPERAND_STACK));

		assertTrue(localVariableAccesses(module, "calculate", 2) > 0);
	}

	@Test
	void supportsCasePatternBindingsWhenDisabled() {
		CompilationFixture module = CompilationFixture.compileSucceeded(
				"""
				type Maybe a = Nothing | Just a
				calculate maybe =
				  case maybe of
				    Nothing -> 0
				    Just value -> add value 1
				answer = calculate (Just 3)
				""",
				CompilationOptions.DEFAULT.disable(Key.OPT_USE_OPERAND_STACK));

		assertEquals(4, module.value("answer"));
	}

	private static int localVariableAccesses(
			CompilationFixture module,
			String methodName,
			int parameterCount
	) {
		Driver.CompilationResult.Succeeded result = assertInstanceOf(
				Driver.CompilationResult.Succeeded.class,
				module.result());
		AtomicInteger accesses = new AtomicInteger();
		new ClassReader(result.clazzes().get("Main")).accept(new ClassVisitor(Opcodes.ASM9) {
			@Override
			public MethodVisitor visitMethod(
					int access,
					String name,
					String descriptor,
					String signature,
					String[] exceptions
			) {
				if(!name.equals(methodName)) {
					return null;
				}
				return new MethodVisitor(Opcodes.ASM9) {
					@Override
					public void visitVarInsn(int opcode, int varIndex) {
						if(varIndex >= parameterCount
								&& (opcode == Opcodes.ALOAD || opcode == Opcodes.ASTORE)) {
							accesses.incrementAndGet();
						}
					}
				};
			}
		}, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
		return accesses.get();
	}
}
