package zlk.jvmcode;

import java.util.function.Consumer;
import java.util.function.Function;

import zlk.util.LocationHolder;

public sealed interface JtExp extends LocationHolder
permits JtCnst, JtLoad, JtStore, JtInSt, JtInVir, JtInItf, JtInDy, JtIf, JtBinOp {

	public <R> R fold(
			Function<? super JtCnst, ? extends R> forCnst,
			Function<? super JtLoad, ? extends R> forLoad,
			Function<? super JtStore, ? extends R> forStore,
			Function<? super JtInSt, ? extends R> forInSt,
			Function<? super JtInVir, ? extends R> forInVir,
			Function<? super JtInItf, ? extends R> forInInt,
			Function<? super JtInDy, ? extends R> forInDy,
			Function<? super JtIf, ? extends R> forIf,
			Function<? super JtBinOp, ? extends R> forBinOp);

	public void match(
			Consumer<? super JtCnst> forCnst,
			Consumer<? super JtLoad> forLoad,
			Consumer<? super JtStore> forStore,
			Consumer<? super JtInSt> forInSt,
			Consumer<? super JtInVir> forInVir,
			Consumer<? super JtInItf> forInInt,
			Consumer<? super JtInDy> forInDy,
			Consumer<? super JtIf> forIf,
			Consumer<? super JtBinOp> forBinOp);
}
