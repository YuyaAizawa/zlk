package zlk.idcalc;

import java.util.function.Consumer;
import java.util.function.Function;

import org.objectweb.asm.MethodVisitor;

import zlk.common.Type;

public record IdBuiltin(
		int id,
		String name,
		Type type,
		Consumer<MethodVisitor> action)
implements IdInfo {

	@Override
	public <R> R map(
			Function<IdFun, R> forFun,
			Function<IdArg, R> forArg,
			Function<IdBuiltin, R> forBuiltin) {
		return forBuiltin.apply(this);
	}

	@Override
	public void match(
			Consumer<IdFun> forFun,
			Consumer<IdArg> forArg,
			Consumer<IdBuiltin> forBuiltin) {
		forBuiltin.accept(this);
	}
}
