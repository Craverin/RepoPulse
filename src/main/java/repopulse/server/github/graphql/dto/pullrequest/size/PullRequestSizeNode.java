package repopulse.server.github.graphql.dto.pullrequest.size;

import repopulse.server.github.graphql.dto.pullrequest.GithubCommitConnection;

import java.time.Instant;

public record PullRequestSizeNode(String fullDatabaseId,
                                  Instant updatedAt,
                                  int additions,
                                  int deletions,
                                  int changedFiles,
                                  GithubCommitConnection commits) { }