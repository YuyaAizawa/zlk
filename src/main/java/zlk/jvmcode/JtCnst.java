package zlk.jvmcode;

import java.util.function.Consumer;
import java.util.function.Function;

import zlk.util.Location;

public sealed interface JtCnst extends JtExp
permits JtcI {

	public static JtCnst i(int value, Location loc) {
		return new JtcI(value, loc);
	}

	@Override
	default public <R> R fold(
			Function<? super JtCnst, ? extends R> forCnst,
			Function<? super JtLoad, ? extends R> forLoad,
			Function<? super JtStore, ? extends R> forStore,
			Function<? super JtInSt, ? extends R> forInSt,
			Function<? super JtInVir, ? extends R> forInVir,
			Function<? super JtInItf, ? extends R> forInInt,
			Function<? super JtInDy, ? extends R> forInDy,
			Function<? super JtIf, ? extends R> forIf,
			Function<? super JtBinOp, ? extends R> forBinOp) {
		return forCnst.apply(this);
	}

	@Override
	default public void match(
			Consumer<? super JtCnst> forCnst,
			Consumer<? super JtLoad> forLoad,
			Consumer<? super JtStore> forStore,
			Consumer<? super JtInSt> forInSt,
			Consumer<? super JtInVir> forInVir,
			Consumer<? super JtInItf> forInInt,
			Consumer<? super JtInDy> forInDy,
			Consumer<? super JtIf> forIf,
			Consumer<? super JtBinOp> forBinOp) {
		forCnst.accept(this);
	}

	public <R> R fold(
			Function<? super Integer, ? extends R> forI);

	public void match(
			Consumer<? super Integer> forI);
}

record JtcI(int value, Location loc) implements JtCnst {

	@Override
	public <R> R fold(Function<? super Integer, ? extends R> forI) {
		return forI.apply(value);
	}

	@Override
	public void match(Consumer<? super Integer> forI) {
		forI.accept(value);
	}
}
