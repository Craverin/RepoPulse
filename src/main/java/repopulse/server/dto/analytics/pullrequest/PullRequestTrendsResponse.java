package repopulse.server.dto.analytics.pullrequest;

import java.time.Instant;
import java.util.List;

public record PullRequestTrendsResponse(long repositoryId,
                                        Instant dataLastSyncedAt,
                                        List<PullRequestMonthlyMetrics> monthlyMetrics) { }
