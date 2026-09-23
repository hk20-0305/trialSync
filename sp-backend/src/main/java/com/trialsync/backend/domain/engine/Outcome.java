package com.trialsync.backend.domain.engine;

import com.trialsync.backend.domain.model.EvidenceReference;
import com.trialsync.backend.domain.model.MissingRequirement;
import com.trialsync.backend.domain.model.ReasonCode;
import com.trialsync.backend.domain.model.TruthValue;
import java.util.List;

/**
 * The internal result of evaluating one rule node.
 *
 * <p>Port of the private {@code _Outcome} dataclass in {@code trialsync.domain.engine}. It carries
 * not just the truth value but the whole audit trail, which is what lets a criterion explain itself
 * without anyone re-deriving evidence later.
 */
record Outcome(
        TruthValue truth,
        ReasonCode reason,
        List<EvidenceReference> evidence,
        List<EvidenceReference> rejected,
        List<MissingRequirement> missing) {

    Outcome {
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        rejected = rejected == null ? List.of() : List.copyOf(rejected);
        missing = missing == null ? List.of() : List.copyOf(missing);
    }

    Outcome(TruthValue truth, ReasonCode reason) {
        this(truth, reason, List.of(), List.of(), List.of());
    }

    /** Port of {@code _invalid}: unknown truth plus a single missing requirement describing why. */
    static Outcome invalid(String fact, String detail, ReasonCode reason) {
        return new Outcome(
                TruthValue.UNKNOWN,
                reason,
                List.of(),
                List.of(),
                List.of(new MissingRequirement(fact, reason, detail)));
    }
}
