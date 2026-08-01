package zlk.phase;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

public final class PhaseResult<T> {

	/**
	 * 成功時の処理結果が無いとき返す用の型
	 */
	public enum Unit {
		INSTANCE
	}

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
	public <R> PhaseResult<R> andThen(Function<? super T, PhaseResult<R>> nextPhase) {
		if(isBlocked()) {
			return blocked();
		}
		return nextPhase.apply(value);
	}

	public <R> R fold(
			Function<? super T, ? extends R> forReady,
			Supplier<? extends R> forBlocked
	) {
		if(isBlocked()) {
			return forBlocked.get();
		}
		return forReady.apply(value);
	}
}