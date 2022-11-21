package zlk.jvmcode;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import zlk.util.Location;

public record JtInSt(
		MethodInfo info,
		List<JtExp> args,
		Location loc)
implements JtExp {

	public String owner() {
		return info.owner();
	}

	public String name() {
		return info.name();
	}

	public String descriptor() {
		return info.descriptor();
	}

	@Override
	public <R> R fold(
			Function<? super JtCnst, ? extends R> forCnst,
			Function<? super JtLoad, ? extends R> forLoad,
			Function<? super JtStore, ? extends R> forStore,
			Function<? super JtInSt, ? extends R> forInSt,
			Function<? super JtInVir, ? extends R> forInVir,
			Function<? super JtInItf, ? extends R> forInInt,
			Function<? super JtInDy, ? extends R> forInDy,
			Function<? super JtIf, ? extends R> forIf,
			Function<? super JtBinOp, ? extends R> forBinOp) {
		return forInSt.apply(this);
	}

	@Override
	public void match(
			Consumer<? super JtCnst> forCnst,
			Consumer<? super JtLoad> forLoad,
			Consumer<? super JtStore> forStore,
			Consumer<? super JtInSt> forInSt,
			Consumer<? super JtInVir> forInVir,
			Consumer<? super JtInItf> forInInt,
			Consumer<? super JtInDy> forInDy,
			Consumer<? super JtIf> forIf,
			Consumer<? super JtBinOp> forBinOp) {
		forInSt.accept(this);
	}
}
