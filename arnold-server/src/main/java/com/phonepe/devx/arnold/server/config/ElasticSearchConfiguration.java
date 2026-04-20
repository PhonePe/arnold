package com.phonepe.devx.arnold.server.config;

import com.phonepe.commons.columbus.ColumbusBundleConfiguration;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import javax.validation.Valid;

@Data
@Builder
@Jacksonized
public class ElasticSearchConfiguration {
    @Valid
    private ColumbusBundleConfiguration columbus;
    private ElasticSearchIndexConfiguration indexConfiguration;
}
