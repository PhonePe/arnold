package com.phonepe.devx.arnold.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.phonepe.devx.arnold.core.config.AerospikeStorageConfiguration;
import com.phonepe.devx.arnold.core.config.GitLabSyncConfiguration;
import com.phonepe.devx.arnold.core.config.GitLabConfiguration;
import lombok.Data;
import lombok.experimental.FieldNameConstants;

@Data
@FieldNameConstants
@JsonIgnoreProperties(ignoreUnknown = true)
public class ArnoldCoreConfiguration {
    private AerospikeStorageConfiguration aerospikeStorageConfiguration;
    private GitLabSyncConfiguration gitlabSyncConfiguration;
    private GitLabConfiguration gitlabConfiguration;
}
