package repopulse.server.dto.analytics.pullrequest;

import java.time.Instant;

public record PullRequestAnalyticsResponse(long repositoryId,
                                           String owner,
                                           String name,
                                           String htmlUrl,
                                           Instant dataLastSyncedAt,
                                           PullRequestAnalytics pullRequestAnalytics) { }
