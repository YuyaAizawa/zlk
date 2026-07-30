package zlk.compiler;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

public final class PhaseResult<T> {
	private static final PhaseResult<?> STOP = new PhaseResult<>(null);

	private T value;

	private PhaseResult(T value) {
		this.value = value;
	}

	public static <T> PhaseResult<T> ready(T value) {
		return new PhaseResult<>(Objects.requireNonNull(value));
	}

	@SuppressWarnings("unchecked")
	public static <T> PhaseResult<T> blocked() {
		return (PhaseResult<T>) STOP;
	}

	/**
	 * 次のフェーズが続行不能であればture
	 * @return
	 */
	public boolean isBlocked() {
		return this == STOP;
	}

	/**
	 * この結果に続けて次のコンパイルフェーズを実行する．この結果が失敗の場合は失敗．
	 * @param <R>
	 * @param nextPhase 次のコンパイルフェーズ
	 * @return 次のコンパイルフェーズと合わせた結果
	 */
	public <R> PhaseResult<R> andThen(Function<T, PhaseResult<R>> nextPhase) {
		if(isBlocked()) {
			return blocked();
		}
		return nextPhase.apply(value);
	}

	public void ifReady(Consumer<? super T> action) {
		if(!isBlocked()) {
			action.accept(value);
		}
	}
}