package zlk.compiler.phase.nameeval;

import zlk.compiler.id.Id;

/**
 * 同じscopeへ同名を再登録しようとしたことを表す．
 */
public final class DuplicatedNameException extends Exception {
	private static final long serialVersionUID = 1L;

	public final Id oldId;
	public final Id newId;

	public DuplicatedNameException(Id oldId, Id newId) {
		super("old: " + oldId + ", new: " + newId);
		this.oldId = oldId;
		this.newId = newId;
	}
}
