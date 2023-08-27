package zlk.recon.constraint.type;

import java.util.List;

import zlk.common.id.Id;

public record AppN(
		Id id,
		List<Type> args)
implements Type {}
