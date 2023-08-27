package zlk.recon.content;

import zlk.recon.flattype.FlatType;

/**
 * 推論中の型で構造を持ったもの
 *
 * @author YuyaAizawa
 */
public record Structure(FlatType flatType) implements Content {}
