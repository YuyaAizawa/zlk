package zlk.ir.anf;

import java.util.Optional;

import zlk.common.Type;
import zlk.common.id.Id;

/**
 * LocalIdで識別される
 */
public record AnfVar(LocalId localId, Optional<Id> id, Type type) implements AnfAtom {}
