package zlk.core;

import java.util.Objects;
import java.util.function.Function;

/**
 * Function placeholder used to implement let-rec bindings during closure conversion.
 */
public final class RecursiveFunction implements Function<Object, Object> {

        private Function<Object, Object> target;

        @Override
        public Object apply(Object arg) {
                if (target == null) {
                        throw new IllegalStateException("recursive function is not initialized yet");
                }
                return target.apply(arg);
        }

        public void setTarget(Function<Object, Object> target) {
                this.target = Objects.requireNonNull(target);
        }
}
