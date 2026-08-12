package repopulse.server.dto.analytics;

import java.time.Instant;

public record RepositoryAnalyticsResponse(long repositoryId,
                                          String owner,
                                          String name,
                                          String htmlUrl,
                                          Instant dataLastSyncedAt,
                                          RepositoryAnalytics analytics) { }
