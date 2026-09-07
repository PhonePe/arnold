package com.phonepe.devx.arnold.models.mergerequest;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.util.List;
import java.util.Map;

@Value
@Builder
@Jacksonized
public class ViewedFilesRequest {
    @NotBlank
    @Pattern(regexp = "[0-9a-fA-F]{40,64}")
    String headSha;
    @NotNull
    @Size(max = 5000)
    List<@NotBlank @Size(max = 1024) String> filePaths;
    @Size(max = 5000)
    List<@NotBlank @Size(max = 1024) String> storyStepIds;
    @Size(max = 5000)
    Map<@NotBlank @Size(max = 1024) String, @NotBlank @Pattern(regexp = "useful|not-useful") String> focusAreaFeedback;
}
