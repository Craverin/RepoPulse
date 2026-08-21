package repopulse.server.dto.analytics.pullrequest;

import java.time.Instant;
import java.util.List;

public record PullRequestInsightsResponse(long repositoryId,
                                          Instant dataLastSyncedAt,
                                          List<PullRequestInsight> pullRequestInsights) { }
