package zlk.recon;

import zlk.common.id.Id;
import zlk.recon.TypeError.InfinitType;

public sealed interface TypeError
permits InfinitType {
	// TODO 無限型の詳細を保持
	record InfinitType(Id id) implements TypeError {}
}
