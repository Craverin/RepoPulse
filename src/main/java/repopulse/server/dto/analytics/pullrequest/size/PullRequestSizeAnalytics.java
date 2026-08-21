package repopulse.server.dto.analytics.pullrequest.size;

import java.util.List;

public record PullRequestSizeAnalytics(PullRequestSizeStatistics sizeStatistics,
                                       List<PullRequestSizeCategoryMetrics> categoryMetrics,
                                       PullRequestSizeImpact sizeImpact,
                                       List<OversizedOpenPullRequest> oversizedOpenPullRequests) { }
