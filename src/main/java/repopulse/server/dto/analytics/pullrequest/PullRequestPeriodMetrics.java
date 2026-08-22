package repopulse.server.dto.analytics.pullrequest;

import java.time.Instant;

public record PullRequestPeriodMetrics(Instant periodStart,
                                       Instant periodEnd,

                                       long pullRequestsCreated,
                                       long pullRequestsMerged,
                                       long pullRequestsClosedWithoutMerge,

                                       long nonDraftOpenPullRequestsAtPeriodEnd,
                                       long stalePullRequestsAtPeriodEnd,

                                       Double mergeRatePercent,
                                       Double medianMergeTimeHours) { }
