package zlk.util;

import zlk.util.Result.Err;
import zlk.util.Result.Ok;

public sealed interface Result<E, V>
permits Ok, Err {
	record Ok<E, V>(V value) implements Result<E, V> {}
	record Err<E, V>(E error) implements Result<E, V> {}

	default V unwrap() {
		switch(this) {
		case Ok(V value) -> {
			return value;
		}
		case Err(E error) -> {
			throw new RuntimeException(error.toString());
		}
		}
	}
}
