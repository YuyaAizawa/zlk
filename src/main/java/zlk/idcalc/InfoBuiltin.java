package zlk.idcalc;

import java.util.function.Consumer;

import org.objectweb.asm.MethodVisitor;

import zlk.common.Type;

public final class InfoBuiltin extends Info {
	private final Consumer<MethodVisitor> action;

	public InfoBuiltin(String name, Type type, Consumer<MethodVisitor> action) {
		super(name, type);
		this.action = action;
	}

	public Consumer<MethodVisitor> action() {
		return action;
	}
}
