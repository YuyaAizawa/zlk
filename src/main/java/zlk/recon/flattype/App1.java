package zlk.recon.flattype;

import java.util.List;

import zlk.common.id.Id;
import zlk.recon.Variable;

public record App1(
		Id id,
		List<Variable> args)
implements FlatType {}
