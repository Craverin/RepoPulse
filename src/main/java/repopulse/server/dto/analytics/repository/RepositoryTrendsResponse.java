package repopulse.server.dto.analytics.repository;

import repopulse.server.dto.analytics.pullrequest.PullRequestMonthlyMetrics;

import java.time.Instant;
import java.util.List;

public record RepositoryTrendsResponse(long repositoryId,
                                       Instant dataLastSyncedAt,
                                       List<PullRequestMonthlyMetrics> monthlyMetrics) { }
