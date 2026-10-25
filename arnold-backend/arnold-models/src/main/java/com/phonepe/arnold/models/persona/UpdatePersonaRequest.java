package com.phonepe.arnold.models.persona;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;
import java.util.List;

@Value
@Builder
@Jacksonized
public class UpdatePersonaRequest {
    @NotBlank
    @Size(max = 80)
    String name;
    @NotBlank
    @Size(max = 240)
    String summary;
    @NotNull
    @Valid
    PersonaPolicy policy;
    @Size(max = 5)
    List<@NotBlank @Size(max = 32) String> tags;
    List<@NotBlank @Size(max = 128) String> contributorIds;
    @Positive
    long version;
}
