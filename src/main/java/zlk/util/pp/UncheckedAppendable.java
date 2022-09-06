package zlk.util.pp;

import java.io.IOException;
import java.io.UncheckedIOException;

interface UncheckedAppendable {

	public UncheckedAppendable append(CharSequence csq);

	static UncheckedAppendable from(Appendable impl) {
		return new UncheckedAppendable() {

			@Override
			public UncheckedAppendable append(CharSequence csq) {
				try {
					impl.append(csq);
					return this;
				} catch(IOException e) {
					throw new UncheckedIOException(e);
				}
			}
		};
	}
}
