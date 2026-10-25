package com.phonepe.arnold.core.model.vcs;

public record ChangeRequestSync(ChangeRequestRecord changeRequest, Action action, String actor) {
    public enum Action {
        UPSERT,
        MERGED,
        CLOSED,
        REOPENED
    }
}
