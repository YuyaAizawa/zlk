package zlk.recon.flattype;

import zlk.recon.Variable;

public record Fun1(
		Variable arg,
		Variable ret)
implements FlatType {}
