package zlk.compiler.ir.reuse;

import java.util.Optional;

import zlk.compiler.id.Id;
import zlk.compiler.ir.typing.Type;

/**
 * ANF上の値をLocalIdで識別する．
 *
 * id == empty はsingle-useを示し，Anf/OwnBlock内で高々1回 value operand として利用される．
 * （式は木構造のとき親に，式またはlet束縛もしくはトップレベルを1つのみ持つため）
 * ただしAnf/OwnBlock.result() はその1回のuseと見なす．
 */
public record LocalVar(int localId, Optional<Id> id, Type type) {

	@Override
	public final boolean equals(Object obj) {
		if(obj == null) {
			return false;
		}
		if(obj == this) {
			return true;
		}
		if(obj instanceof LocalVar other) {
			return other.localId == this.localId;
		}
		return false;
	}

	@Override
	public final int hashCode() {
		return Integer.hashCode(localId);
	}
}
