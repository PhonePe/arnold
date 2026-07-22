package com.phonepe.devx.arnold.core.services.impl;

import com.phonepe.devx.arnold.core.exception.ArnoldError;
import com.phonepe.devx.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.devx.arnold.core.services.AnalysisDispatcher;

import javax.inject.Singleton;

@Singleton
public class UnavailableAnalysisDispatcher implements AnalysisDispatcher {
    @Override
    public void dispatch(final String mergeRequestId, final String runId, final String personaId) {
        throw ArnoldError.raise(ArnoldErrorCode.CLIENT_FAILURE)
                .message("Analysis dispatcher is not configured")
                .build();
    }
}
