package repopulse.server.dto.analytics;

import java.util.List;

public record RepositoryAnalytics(long totalPullRequests,
                                  long openPullRequests,
                                  long openDraftPullRequests,
                                  long mergedPullRequests,
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

                                  List<StalePullRequestResponse> stalestPullRequests) { }
