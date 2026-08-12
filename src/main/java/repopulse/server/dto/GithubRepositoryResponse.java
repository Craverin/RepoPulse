package repopulse.server.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GithubRepositoryResponse(Long id,
                                       GithubOwnerResponse owner,
                                       String name,
                                       @JsonProperty("html_url") String htmlUrl,
                                       @JsonProperty("default_branch") String defaultBranch) { }
