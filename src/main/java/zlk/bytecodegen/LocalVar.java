package zlk.bytecodegen;

import zlk.common.type.Type;

/**
 * バイトコード上で local variable として扱われる変数
 * @author YuyaAizawa
 *
 */
public record LocalVar (
		int idx,
		Type type) { // TODO: add RuntimeType
}
