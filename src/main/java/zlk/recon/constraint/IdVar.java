package zlk.recon.constraint;

import zlk.common.id.Id;
import zlk.recon.Variable;

public record IdVar(
		Id id,
		Variable var) {}
