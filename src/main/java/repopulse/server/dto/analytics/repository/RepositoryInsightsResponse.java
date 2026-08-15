package repopulse.server.dto.analytics.repository;

import repopulse.server.dto.analytics.pullrequest.PullRequestInsight;

import java.time.Instant;
import java.util.List;

public record RepositoryInsightsResponse(long repositoryId,
                                         Instant dataLastSyncedAt,
                                         List<PullRequestInsight> pullRequestInsights) { }
