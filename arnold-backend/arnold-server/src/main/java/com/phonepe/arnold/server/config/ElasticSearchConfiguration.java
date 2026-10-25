package com.phonepe.arnold.server.config;

import com.phonepe.commons.columbus.ColumbusBundleConfiguration;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

@Data
@Builder
@Jacksonized
public class ElasticSearchConfiguration {
    @Valid
    @NotNull
    private ColumbusBundleConfiguration columbus;
    private ElasticSearchIndexConfiguration indexConfiguration;
}
