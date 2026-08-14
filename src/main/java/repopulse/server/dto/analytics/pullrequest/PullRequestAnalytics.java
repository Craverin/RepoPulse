package repopulse.server.dto.analytics.pullrequest;

import repopulse.server.dto.StalePullRequest;

import java.util.List;

public record PullRequestAnalytics(long totalPullRequests,
                                   long openPullRequests,
                                   long openDraftPullRequests,
                                   long mergedPullRequests,
                                   long mergedDraftPullRequests,
                                   long closedWithoutMergePullRequests,

                                   Double mergeRatePercent,
                                   Double averageMergeTimeHours,
                                   Double medianMergeTimeHours,

                                   long freshOpenPullRequests,
                                   long agingOpenPullRequests,
                                   long staleOpenPullRequests,
                                   long veryStaleOpenPullRequests,
                                   Double staleOpenPullRequestRatePercent,

                                   long createdLast30Days,
                                   long mergedLast30Days,
                                   long closedWithoutMergeLast30Days,
                                   long uniquePullRequestAuthors,

                                   List<StalePullRequest> stalestPullRequests) { }
