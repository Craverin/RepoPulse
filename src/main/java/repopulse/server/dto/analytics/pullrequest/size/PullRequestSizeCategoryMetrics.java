package repopulse.server.dto.analytics.pullrequest.size;

public record PullRequestSizeCategoryMetrics(PullRequestSizeCategory category,

                                             int completedPullRequests,
                                             Double completedSharePercent,

                                             int mergedPullRequests,
                                             int closedWithoutMergePullRequests,
                                             Double mergeRatePercent,
                                             Double medianMergeTimeHours,

                                             int nonDraftOpenPullRequests,
                                             int nonDraftStaleOpenPullRequests,
                                             Double staleOpenPullRequestRatePercent) { }
