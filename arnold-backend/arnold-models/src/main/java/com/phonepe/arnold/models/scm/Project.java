package com.phonepe.arnold.models.scm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * Repository/project metadata used to enrich review feedback.
 * {@code teamId} and {@code pod} are optional ownership attributes that a metadata source may supply.
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Project {
    private Integer id;
    private String name;
    private String description;

    @JsonProperty("web_url")
    private String webUrl;
    private String namespace;

    @JsonProperty("path_with_namespace")
    private String pathWithNamespace;

    @JsonProperty("team_id")
    private String teamId;

    @JsonProperty("pod")
    private String pod;

    @JsonProperty("last_activity_at")
    private Date lastActivityAt;
}
