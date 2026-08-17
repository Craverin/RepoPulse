package repopulse.server.github.graphql.dto.pullrequest;

import java.time.Instant;

public record GithubPullRequestNode(String fullDatabaseId,
                                    int number,
                                    String state,
                                    String title,
                                    String url,
                                    GithubActor author,
                                    boolean isDraft,
                                    Instant createdAt,
                                    Instant updatedAt,
                                    Instant closedAt,
                                    Instant mergedAt,
                                    int additions,
                                    int deletions,
                                    int changedFiles,
                                    GithubCommitConnection commits) { }
