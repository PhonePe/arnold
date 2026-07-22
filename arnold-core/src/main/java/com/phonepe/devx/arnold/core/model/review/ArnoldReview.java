package com.phonepe.devx.arnold.core.model.review;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.livetheoogway.crudstore.core.Id;

/**
 * Durable representation of an MR review's position in the state machine, persisted via crud-store.
 * {@code id} is the MR key (e.g. {@code projectId:mrId}); {@code state} and {@code data} together form
 * the {@link com.phonepe.devx.arnold.core.statemachine.StateInfo} — {@code data} carries the analysis
 * run id, outcome and pre-close state that fencing and reopen depend on. Concurrency is handled by
 * crud-store's record version (Aerospike generation), not a field here.
 */
public record ArnoldReview(
        String id,
        ReviewState state,
        ReviewStateData data
) implements Id {

    @JsonCreator
    public ArnoldReview(@JsonProperty("id") final String id,
                        @JsonProperty("state") final ReviewState state,
                        @JsonProperty("data") final ReviewStateData data) {
        this.id = id;
        this.state = state;
        this.data = data;
    }
}
