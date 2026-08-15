package repopulse.server.dto.analytics.pullrequest;

import java.time.Instant;

public record PullRequestPeriodMetrics(Instant periodStart,
                                       Instant periodEnd,
                                       long pullRequestsMerged,
                                       long pullRequestsClosedWithoutMerge,
                                       long openPullRequestsAtPeriodEnd,

                                       Double mergeRatePercent,
                                       Double medianMergeTimeHours) { }
