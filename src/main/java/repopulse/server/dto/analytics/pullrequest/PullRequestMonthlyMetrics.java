package repopulse.server.dto.analytics.pullrequest;

import java.time.YearMonth;

public record PullRequestMonthlyMetrics(YearMonth month,
                                        long pullRequestsCreated,
                                        long pullRequestsMerged,
                                        long pullRequestsClosedWithoutMerge,
                                        long openPullRequestsAtMonthEnd,

                                        Double mergeRatePercent,
                                        Double medianMergeTimeHours) { }
