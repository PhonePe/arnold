/*
 * Copyright (c) 2026 Original Author(s), PhonePe India Pvt. Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.phonepe.arnold.core.model.review;

/**
 * Triggers that drive {@link ReviewState} transitions. Modeled as explicit events (rather than keyed
 * off action classes) so that two arrows leaving the same state are unambiguous — most notably the two
 * author-driven moves out of {@code ANALYSED}: re-run ({@link #INVOKE_ANALYSIS}) vs publish
 * ({@link #MARK_REVIEW_READY}).
 *
 * <p>{@link Source} captures who is expected to raise each event, which also dictates how it should be
 * fired:
 * <ul>
 *   <li>{@link Source#USER} commands are fired strictly ({@code fire}); an invalid one is an error.</li>
 *   <li>{@link Source#PROVIDER_SYNC} facts are authoritative and fired leniently ({@code tryFire}) as
 *       global transitions — they can arrive in almost any state and a stale/duplicate one is ignored,
 *       never rejected.</li>
 *   <li>{@link Source#ARNOLD_INTERNAL} async callbacks are fired leniently too; a late callback for a
 *       superseded run or an already-closed MR is ignored.</li>
 * </ul>
 *
 * <p>There is deliberately no {@code ANALYSIS_FAILED} event: the orchestrator always emits
 * {@link #ANALYSIS_COMPLETED}, carrying the success/partial/failure outcome in its payload.
 */
public enum ReviewEvent {

    INVOKE_ANALYSIS(Source.USER),
    MARK_REVIEW_READY(Source.USER),
    ANALYSIS_COMPLETED(Source.ARNOLD_INTERNAL),
    CHANGE_MERGED(Source.PROVIDER_SYNC),
    CHANGE_CLOSED(Source.PROVIDER_SYNC),
    CHANGE_REOPENED(Source.PROVIDER_SYNC);

    private final Source source;

    ReviewEvent(final Source source) {
        this.source = source;
    }

    public Source source() {
        return source;
    }

    public enum Source {
        USER,
        ARNOLD_INTERNAL,
        PROVIDER_SYNC
    }
}
