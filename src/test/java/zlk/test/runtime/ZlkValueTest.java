package zlk.test.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import zlk.runtime.ZlkValue;

public class ZlkValueTest {
	@Test
	void runtimeValueStringAppenderDispatches() {
		StringBuilder sb = new StringBuilder();
		ZlkValue.appendStringTo(sb, Integer.valueOf(2));
		ZlkValue.appendStringTo(sb.append(' '), Boolean.TRUE);
		ZlkValue.appendStringTo(sb.append(' '), "java");
		assertEquals("2 True java", sb.toString());
	}
}
