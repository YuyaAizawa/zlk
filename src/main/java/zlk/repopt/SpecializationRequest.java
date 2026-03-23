package zlk.repopt;

import java.util.BitSet;
import java.util.EnumSet;
import java.util.Map;

import zlk.common.Type;
import zlk.common.id.Id;

public record SpecializationRequest(
    Id originalId,
    Id specializedId,
    EnumSet<RepKey> keys,

    Type instantiatedType,   // この call cluster / SCC で見えた具体化後の型
    BitSet unboxedParams,
    boolean unboxedResult,

    Map<Type.Var, Type> fixedTyArgs
) {}