package repopulse.server.github.graphql.dto.pullrequest.summary;

import repopulse.server.github.graphql.dto.pullrequest.GithubActor;

import java.time.Instant;

public record PullRequestSummaryNode(String fullDatabaseId,
                                     int number,
                                     String state,
                                     String title,
                                     String url,
                                     GithubActor author,
                                     boolean isDraft,
                                     Instant createdAt,
                                     Instant updatedAt,
                                     Instant closedAt,
                                     Instant mergedAt) { }
