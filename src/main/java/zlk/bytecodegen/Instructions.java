package zlk.bytecodegen;

import java.util.function.Consumer;

import org.objectweb.asm.MethodVisitor;

@FunctionalInterface
public interface Instructions extends Consumer<MethodVisitor> {

	default void insert(MethodVisitor mv) {
		accept(mv);
	}
}
