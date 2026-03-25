package com.phonepe.devx.arnold.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.experimental.FieldNameConstants;

@Data
@FieldNameConstants
@JsonIgnoreProperties(ignoreUnknown = true)
public class ArnoldCoreConfiguration {
    boolean hello; // todo clean this up
}
