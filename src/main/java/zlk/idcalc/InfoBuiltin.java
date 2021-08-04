package zlk.idcalc;

import java.util.function.Consumer;
import java.util.function.Function;

import org.objectweb.asm.MethodVisitor;

import zlk.common.Type;

public record InfoBuiltin(
		String name,
		Type type,
		Consumer<MethodVisitor> action)
implements Info {

	@Override
	public <R> R map(
			Function<InfoFun, R> forFun,
			Function<InfoArg, R> forArg,
			Function<InfoBuiltin, R> forBuiltin) {
		return forBuiltin.apply(this);
	}

	@Override
	public void match(
			Consumer<InfoFun> forFun,
			Consumer<InfoArg> forArg,
			Consumer<InfoBuiltin> forBuiltin) {
		forBuiltin.accept(this);
	}
}
