package repopulse.server.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record GithubPullRequestResponse(long id,
                                        int number,
                                        String state,
                                        String title,
                                        @JsonProperty("html_url") String htmlUrl,
                                        GithubOwnerResponse user,
                                        boolean draft,
                                        @JsonProperty("updated_at") Instant updatedAt,
                                        @JsonProperty("created_at") Instant createdAt,
                                        @JsonProperty("closed_at") Instant closedAt,
                                        @JsonProperty("merged_at") Instant mergedAt) { }
