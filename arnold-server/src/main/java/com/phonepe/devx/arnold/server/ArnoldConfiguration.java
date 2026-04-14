package com.phonepe.devx.arnold.server;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.phonepe.devx.arnold.core.ArnoldCoreConfiguration;
import com.phonepe.devx.arnold.server.config.ElasticSearchConfiguration;
import lombok.Data;
import lombok.experimental.FieldNameConstants;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

@Data
@FieldNameConstants
@JsonIgnoreProperties(ignoreUnknown = true)
public class ArnoldConfiguration extends BaseConfiguration {

    @Valid
    @NotNull
    private ArnoldCoreConfiguration serviceCoreConfiguration;

    @Valid
    private ElasticSearchConfiguration elasticSearchConfiguration;

}
