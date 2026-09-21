package com.phonepe.devx.arnold.core.model.analysis;

import com.livetheoogway.crudstore.core.Id;
import com.phonepe.devx.arnold.models.analysis.GroupAnalysisResponse;
import lombok.With;

import java.util.List;

@With
public record GroupAnalysisRun(String id, String invokedBy, GroupAnalysisResponse.StartRequest context,
                               List<GroupAnalysisResponse.Persona> reviewPersonas,
                               GroupAnalysisResponse result, String workerId, long sequence,
                               boolean publicationOnly, long executionStartedAt) implements Id { }
