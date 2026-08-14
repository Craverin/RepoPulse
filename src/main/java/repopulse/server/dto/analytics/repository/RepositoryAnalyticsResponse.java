package repopulse.server.dto.analytics.repository;

import repopulse.server.dto.analytics.pullrequest.PullRequestAnalytics;

import java.time.Instant;

public record RepositoryAnalyticsResponse(long repositoryId,
                                          String owner,
                                          String name,
                                          String htmlUrl,
                                          Instant dataLastSyncedAt,
                                          PullRequestAnalytics pullRequestAnalytics) { }
