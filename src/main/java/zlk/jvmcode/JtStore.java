package zlk.jvmcode;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import zlk.jvmcode.type.JtType;
import zlk.util.Location;

public record JtStore(
		int index,
		Optional<String> varName,
		JtType type,
		Location loc)
implements JtExp {

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
		return forStore.apply(this);
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
		forStore.accept(this);
	}
}
