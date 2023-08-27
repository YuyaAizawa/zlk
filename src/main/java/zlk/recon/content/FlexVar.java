package zlk.recon.content;

/**
 * 任意の型．自動生成で名前が付けられていないときはmaybeNameはnull．
 * @author YuyaAizawa
 *
 */
public record FlexVar(int id, String maybeName) implements Content {
	public FlexVar(int id) {
		this(id, null);
	}
}
