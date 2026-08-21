package repopulse.server.dto.analytics.pullrequest.size;

import java.time.Instant;

public record PullRequestSizeAnalyticsResponse(long repositoryId,
                                               String owner,
                                               String name,
                                               String htmlUrl,

                                               Instant periodStart,
                                               Instant periodEnd,
                                               Instant dataLastSyncedAt,

                                               PullRequestSizeAnalytics analytics) { }
