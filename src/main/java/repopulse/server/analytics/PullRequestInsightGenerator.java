package repopulse.server.analytics;

import org.springframework.stereotype.Component;
import repopulse.server.analytics.statistics.Statistics;
import repopulse.server.dto.analytics.pullrequest.*;
import repopulse.server.dto.analytics.pullrequest.size.PullRequestSizeAnalytics;
import repopulse.server.dto.analytics.pullrequest.size.PullRequestSizeCategory;
import repopulse.server.dto.analytics.pullrequest.size.PullRequestSizeCategoryMetrics;
import repopulse.server.dto.analytics.pullrequest.size.PullRequestSizeImpact;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

@Component
public class PullRequestInsightGenerator
{
    private static final int OVERSIZED_THRESHOLD_CHANGED_LINES = 500;

    public List<PullRequestInsight> generate(PullRequestPeriodMetrics currentPeriod,
                                             PullRequestPeriodMetrics previousPeriod,
                                             PullRequestSizeAnalytics sizeAnalytics)
    {
        InsightContext context = new InsightContext(
                currentPeriod,
                previousPeriod,
                sizeAnalytics == null
                    ? null
                    : createSizeInsightMetrics(sizeAnalytics)
        );

        List<Function<InsightContext, PullRequestInsight>> insightFunctions = List.of(
                this::createBacklogGrowingInsight,
                this::createMergeTimeHighInsight,
                this::createMergeTimeIncreasedInsight,
                this::createMergeRateLowInsight,
                this::createMergeRateDroppedInsight,
                this::createStaleOpenPullRequestRateHighInsight,
                this::createMergeThroughputDroppedInsight,
                this::createOversizedPullRequestShareHighInsight,
                this::createOversizedMergeTimeHigherInsight,
                this::createOversizedMergeRateLowerInsight,
                this::createSizeMergeTimeAssociationInsight,
                this::createStaleOversizedOpenPullRequestsInsight
        );

        return insightFunctions.stream()
                .map(function -> function.apply(context))
                .filter(Objects::nonNull)
                .toList();
    }

    private PullRequestInsight createBacklogGrowingInsight(InsightContext context)
    {
        long currentOpen = context.currentPeriod().nonDraftOpenPullRequestsAtPeriodEnd();
        long previousOpen = context.previousPeriod().nonDraftOpenPullRequestsAtPeriodEnd();
        long delta = currentOpen - previousOpen;

        double growthPercent;
        if (previousOpen == 0)
            growthPercent = delta > 0 ? 100.0 : 0.0;
        else
            growthPercent = delta * 100.0 / previousOpen;

        InsightSeverity severity = getBacklogGrowingSeverity(delta, growthPercent);

        if (severity == null)
            return null;

        String description;

        if (previousOpen == 0)
        {
            description = String.format(
                    Locale.ROOT,
                    "Open pull request backlog grew from 0 to %d over the last 30 days.",
                    currentOpen
            );
        }

        else
        {
            description = String.format(
                    Locale.ROOT,
                    "Open pull request backlog grew from %d to %d over the last " +
                            "30 days (+%.1f%%).",
                    previousOpen,
                    currentOpen,
                    growthPercent
            );
        }

        return new PullRequestInsight(
                PullRequestInsightType.BACKLOG_GROWING,
                InsightCategory.PERIOD_COMPARISON,
                severity,
                description,
                (double) currentOpen,
                (double) previousOpen,
                InsightValueUnit.COUNT
        );
    }

    private PullRequestInsight createMergeTimeHighInsight(InsightContext context)
    {
        PullRequestPeriodMetrics currentPeriod = context.currentPeriod();

        if (currentPeriod.medianMergeTimeHours() == null)
            return null;

        InsightSeverity severity = getMergeTimeHighSeverity(
                currentPeriod.medianMergeTimeHours(),
                currentPeriod.pullRequestsMerged()
        );

        if (severity == null)
            return null;

        String description = String.format(
                Locale.ROOT,
                "Median merge time for pull requests merged in the last " +
                        "30 days is %.1f hours, exceeding the configured " +
                        "72-hour threshold.",
                currentPeriod.medianMergeTimeHours()
        );

        return new PullRequestInsight(
                PullRequestInsightType.MEDIAN_MERGE_TIME_HIGH,
                InsightCategory.CURRENT_STATE,
                severity,
                description,
                currentPeriod.medianMergeTimeHours(),
                null,
                InsightValueUnit.HOURS
        );
    }

    private PullRequestInsight createMergeTimeIncreasedInsight(InsightContext context)
    {
        PullRequestPeriodMetrics currentPeriod = context.currentPeriod();
        PullRequestPeriodMetrics previousPeriod = context.previousPeriod();

        Double currentMedian = currentPeriod.medianMergeTimeHours();
        Double previousMedian = previousPeriod.medianMergeTimeHours();

        if (currentMedian == null || previousMedian == null || previousMedian <= 0)
            return null;

        double increaseHours = currentMedian - previousMedian;
        double increasePercent = increaseHours * 100.0 / previousMedian;

        InsightSeverity severity = getMergeTimeIncreasedSeverity(
                increasePercent,
                increaseHours,
                currentMedian,
                currentPeriod.pullRequestsMerged(),
                previousPeriod.pullRequestsMerged()
        );

        if (severity == null)
            return null;

        String description = String.format(
                Locale.ROOT,
                "Median merge time increased from %.1f to %.1f hours compared " +
                        "with the previous 30-day period (+%.1f%%).",
                previousMedian,
                currentMedian,
                increasePercent
        );

        return new PullRequestInsight(
                PullRequestInsightType.MEDIAN_MERGE_TIME_INCREASED,
                InsightCategory.PERIOD_COMPARISON,
                severity,
                description,
                currentMedian,
                previousMedian,
                InsightValueUnit.HOURS
        );
    }

    private PullRequestInsight createMergeRateLowInsight(InsightContext context)
    {
        PullRequestPeriodMetrics currentPeriod = context.currentPeriod();
        Double mergeRate = currentPeriod.mergeRatePercent();

        if (mergeRate == null)
            return null;

        long completed = getCompletedPullRequests(currentPeriod);

        InsightSeverity severity = getMergeRateLowSeverity(mergeRate, completed);

        if (severity == null)
            return null;

        String description = String.format(
                Locale.ROOT,
                "Merge rate for the last 30 days is %.1f%%, below the " +
                        "configured 80%% threshold: %d of %d completed pull " +
                        "requests were merged.",
                mergeRate,
                currentPeriod.pullRequestsMerged(),
                completed
        );

        return new PullRequestInsight(
                PullRequestInsightType.MERGE_RATE_LOW,
                InsightCategory.CURRENT_STATE,
                severity,
                description,
                mergeRate,
                null,
                InsightValueUnit.PERCENT
        );
    }

    private PullRequestInsight createMergeRateDroppedInsight(InsightContext context)
    {
        PullRequestPeriodMetrics currentPeriod = context.currentPeriod();
        PullRequestPeriodMetrics previousPeriod = context.previousPeriod();

        Double currentRate = currentPeriod.mergeRatePercent();
        Double previousRate = previousPeriod.mergeRatePercent();

        if (currentRate == null || previousRate == null)
            return null;

        long currentCompleted = getCompletedPullRequests(currentPeriod);
        long previousCompleted = getCompletedPullRequests(previousPeriod);

        double dropPercentagePoints = previousRate - currentRate;

        InsightSeverity severity = getMergeRateDroppedSeverity(
                dropPercentagePoints,
                currentCompleted,
                previousCompleted
        );

        if (severity == null)
            return null;

        String description = String.format(
                Locale.ROOT,
                "Merge rate dropped from %.1f%% to %.1f%% compared with the " +
                        "previous 30-day period (-%.1f percentage points).",
                previousRate,
                currentRate,
                dropPercentagePoints
        );

        return new PullRequestInsight(
                PullRequestInsightType.MERGE_RATE_DROPPED,
                InsightCategory.PERIOD_COMPARISON,
                severity,
                description,
                currentRate,
                previousRate,
                InsightValueUnit.PERCENT
        );
    }

    private PullRequestInsight createStaleOpenPullRequestRateHighInsight(InsightContext context)
    {
        PullRequestPeriodMetrics currentPeriod = context.currentPeriod();

        long openPullRequests = currentPeriod.nonDraftOpenPullRequestsAtPeriodEnd();
        long stalePullRequests = currentPeriod.stalePullRequestsAtPeriodEnd();

        if (openPullRequests == 0)
            return null;

        double staleRatePercent = stalePullRequests * 100.0 / openPullRequests;

        InsightSeverity severity = getStalePullRequestRateSeverity(
                staleRatePercent,
                stalePullRequests,
                openPullRequests
        );

        if (severity == null)
            return null;

        String description = String.format(
                Locale.ROOT,
                "%.1f%% of ready-for-review open pull requests are stale " +
                        "(%d of %d).",
                staleRatePercent,
                stalePullRequests,
                openPullRequests
        );

        return new PullRequestInsight(
                PullRequestInsightType.STALE_OPEN_PULL_REQUEST_RATE_HIGH,
                InsightCategory.CURRENT_STATE,
                severity,
                description,
                staleRatePercent,
                null,
                InsightValueUnit.PERCENT
        );
    }

    private PullRequestInsight createMergeThroughputDroppedInsight(InsightContext context)
    {
        PullRequestPeriodMetrics currentPeriod = context.currentPeriod();
        PullRequestPeriodMetrics previousPeriod = context.previousPeriod();

        long currentMerged = currentPeriod.pullRequestsMerged();
        long previousMerged = previousPeriod.pullRequestsMerged();

        long throughputDrop = previousMerged - currentMerged;

        if (throughputDrop <= 0 || previousMerged == 0)
            return null;

        long currentCreated = currentPeriod.pullRequestsCreated();
        long previousCreated = previousPeriod.pullRequestsCreated();

        boolean creationVolumeStayedComparable = previousCreated > 0
                        && currentCreated >= previousCreated * 0.8;

        boolean backlogGrew = currentPeriod.nonDraftOpenPullRequestsAtPeriodEnd()
                > previousPeriod.nonDraftOpenPullRequestsAtPeriodEnd();

        if (!creationVolumeStayedComparable && !backlogGrew)
            return null;

        double throughputDropPercent = throughputDrop * 100.0 / previousMerged;

        InsightSeverity severity = getMergeThroughputDroppedSeverity(
                throughputDropPercent,
                throughputDrop,
                previousMerged
        );

        if (severity == null)
            return null;

        String description = String.format(
                Locale.ROOT,
                "Merge throughput dropped from %d to %d merged pull requests " +
                        "compared with the previous 30-day period (-%.1f%%).",
                previousMerged,
                currentMerged,
                throughputDropPercent
        );

        return new PullRequestInsight(
                PullRequestInsightType.MERGE_THROUGHPUT_DROPPED,
                InsightCategory.PERIOD_COMPARISON,
                severity,
                description,
                (double) currentMerged,
                (double) previousMerged,
                InsightValueUnit.COUNT
        );
    }

    private PullRequestInsight createOversizedPullRequestShareHighInsight(InsightContext context)
    {
        SizeInsightMetrics metrics = context.sizeMetrics();

        if (metrics == null || metrics.totalCompleted() == 0)
            return null;

        double oversizedSharePercent = metrics.oversizedCompleted() * 100.0 / metrics.totalCompleted();

        InsightSeverity severity = getOversizedShareSeverity(
                oversizedSharePercent,
                metrics.oversizedCompleted(),
                metrics.totalCompleted()
        );

        if (severity == null)
            return null;

        String description = String.format(
                Locale.ROOT,
                "%.1f%% of completed pull requests contained more than %d " +
                        "changed lines (%d of %d).",
                oversizedSharePercent,
                OVERSIZED_THRESHOLD_CHANGED_LINES,
                metrics.oversizedCompleted(),
                metrics.totalCompleted()
        );

        return new PullRequestInsight(
                PullRequestInsightType.OVERSIZED_PULL_REQUEST_SHARE_HIGH,
                InsightCategory.SIZE_IMPACT,
                severity,
                description,
                oversizedSharePercent,
                null,
                InsightValueUnit.PERCENT
        );
    }

    private PullRequestInsight createOversizedMergeTimeHigherInsight(InsightContext context)
    {
        SizeInsightMetrics metrics = context.sizeMetrics();

        if (metrics == null)
            return null;

        Double oversizedMedian = metrics.oversizedMedianMergeTimeHours();
        Double nonOversizedMedian = metrics.nonOversizedMedianMergeTimeHours();
        Double ratio = metrics.oversizedToNonOversizedMedianMergeTimeRatio();

        if (oversizedMedian == null || nonOversizedMedian == null || ratio == null)
            return null;

        double differenceHours = oversizedMedian - nonOversizedMedian;

        InsightSeverity severity = getOversizedMergeTimeSeverity(
                ratio,
                differenceHours,
                metrics.oversizedMerged(),
                metrics.nonOversizedMerged()
        );

        if (severity == null)
            return null;

        String description = String.format(
                Locale.ROOT,
                "Oversized pull requests took %.2fx longer to merge: " +
                        "%.1f versus %.1f median hours.",
                ratio,
                oversizedMedian,
                nonOversizedMedian
        );

        return new PullRequestInsight(
                PullRequestInsightType.OVERSIZED_MERGE_TIME_HIGHER,
                InsightCategory.SIZE_IMPACT,
                severity,
                description,
                oversizedMedian,
                nonOversizedMedian,
                InsightValueUnit.HOURS
        );
    }

    private PullRequestInsight createOversizedMergeRateLowerInsight(InsightContext context)
    {
        SizeInsightMetrics metrics = context.sizeMetrics();

        if (metrics == null || metrics.oversizedCompleted() == 0 || metrics.nonOversizedCompleted() == 0)
            return null;

        double oversizedMergeRate = metrics.oversizedMerged() * 100.0 / metrics.oversizedCompleted();
        double nonOversizedMergeRate = metrics.nonOversizedMerged() * 100.0 / metrics.nonOversizedCompleted();
        double dropPercentagePoints = nonOversizedMergeRate - oversizedMergeRate;

        InsightSeverity severity = getOversizedMergeRateSeverity(
                dropPercentagePoints,
                metrics.oversizedCompleted(),
                metrics.nonOversizedCompleted()
        );

        if (severity == null)
            return null;

        String description = String.format(
                Locale.ROOT,
                "Oversized pull requests had a %.1f%% merge rate versus " +
                        "%.1f%% for smaller pull requests " +
                        "(-%.1f percentage points).",
                oversizedMergeRate,
                nonOversizedMergeRate,
                dropPercentagePoints
        );

        return new PullRequestInsight(
                PullRequestInsightType.OVERSIZED_MERGE_RATE_LOWER,
                InsightCategory.SIZE_IMPACT,
                severity,
                description,
                Statistics.roundToHundredth(oversizedMergeRate),
                Statistics.roundToHundredth(nonOversizedMergeRate),
                InsightValueUnit.PERCENT
        );
    }

    private PullRequestInsight createSizeMergeTimeAssociationInsight(InsightContext context)
    {
        SizeInsightMetrics metrics = context.sizeMetrics();

        if (metrics == null || metrics.changedLinesToMergeTimeCorrelation() == null)
            return null;

        if (metrics.oversizedMedianMergeTimeHours() != null
                && metrics.nonOversizedMedianMergeTimeHours() != null
                && metrics.oversizedToNonOversizedMedianMergeTimeRatio() != null)
        {
            double differenceHours = metrics.oversizedMedianMergeTimeHours()
                    - metrics.nonOversizedMedianMergeTimeHours();

            InsightSeverity medianComparisonSeverity = getOversizedMergeTimeSeverity(
                    metrics.oversizedToNonOversizedMedianMergeTimeRatio(),
                    differenceHours,
                    metrics.oversizedMerged(),
                    metrics.nonOversizedMerged()
            );

            if (medianComparisonSeverity != null)
                return null;
        }

        double correlation = metrics.changedLinesToMergeTimeCorrelation();

        InsightSeverity severity = getSizeMergeTimeAssociationSeverity(
                correlation,
                metrics.correlationSampleSize()
        );

        if (severity == null)
            return null;

        String description = String.format(
                Locale.ROOT,
                "Larger pull requests were associated with longer merge " +
                        "times (Spearman correlation = %.2f, sample size = %d).",
                correlation,
                metrics.correlationSampleSize()
        );

        return new PullRequestInsight(
                PullRequestInsightType.SIZE_ASSOCIATED_WITH_LONGER_MERGE_TIME,
                InsightCategory.SIZE_IMPACT,
                severity,
                description,
                correlation,
                null,
                InsightValueUnit.COEFFICIENT
        );
    }

    private PullRequestInsight createStaleOversizedOpenPullRequestsInsight(InsightContext context)
    {
        SizeInsightMetrics metrics = context.sizeMetrics();

        if (metrics == null || metrics.oversizedOpen() == 0 || metrics.staleOversizedOpen() == 0)
            return null;

        double staleRatePercent = metrics.staleOversizedOpen() * 100.0 / metrics.oversizedOpen();

        InsightSeverity severity = getStaleOversizedOpenPullRequestsSeverity(
                metrics.staleOversizedOpen(),
                staleRatePercent
        );

        if (severity == null)
            return null;

        String description = String.format(
                Locale.ROOT,
                "%d oversized ready-for-review open pull requests are stale " +
                        "(%.1f%% of oversized open pull requests).",
                metrics.staleOversizedOpen(),
                staleRatePercent
        );

        return new PullRequestInsight(
                PullRequestInsightType.STALE_OVERSIZED_OPEN_PULL_REQUESTS,
                InsightCategory.SIZE_IMPACT,
                severity,
                description,
                (double) metrics.staleOversizedOpen(),
                (double) metrics.oversizedOpen(),
                InsightValueUnit.COUNT
        );
    }

    private SizeInsightMetrics createSizeInsightMetrics(PullRequestSizeAnalytics analytics)
    {
        long oversizedCompleted = 0;
        long nonOversizedCompleted = 0;

        long oversizedMerged = 0;
        long nonOversizedMerged = 0;

        long oversizedOpen = 0;
        long staleOversizedOpen = 0;

        for (PullRequestSizeCategoryMetrics categoryMetrics : analytics.categoryMetrics())
        {
            boolean oversized = categoryMetrics.category().equals(PullRequestSizeCategory.LARGE)
                    || categoryMetrics.category().equals(PullRequestSizeCategory.ENORMOUS);

            if (oversized)
            {
                oversizedCompleted += categoryMetrics.completedPullRequests();
                oversizedMerged += categoryMetrics.mergedPullRequests();
                oversizedOpen += categoryMetrics.nonDraftOpenPullRequests();
                staleOversizedOpen += categoryMetrics.nonDraftStaleOpenPullRequests();
            }

            else
            {
                nonOversizedCompleted += categoryMetrics.completedPullRequests();
                nonOversizedMerged += categoryMetrics.mergedPullRequests();
            }
        }

        PullRequestSizeImpact impact = analytics.sizeImpact();

        return new SizeInsightMetrics(
                oversizedCompleted + nonOversizedCompleted,
                oversizedCompleted,
                nonOversizedCompleted,

                oversizedMerged,
                nonOversizedMerged,

                oversizedOpen,
                staleOversizedOpen,

                impact == null
                        ? null
                        : impact.oversizedMedianMergeTimeHours(),

                impact == null
                        ? null
                        : impact.nonOversizedMedianMergeTimeHours(),

                impact == null
                        ? null
                        : impact.oversizedToNonOversizedMedianMergeTimeRatio(),

                impact == null
                        ? 0
                        : impact.correlationSampleSize(),

                impact == null
                        ? null
                        : impact.changedLinesToMergeTimeSpearmanCorrelation()
        );
    }

    private long getCompletedPullRequests(PullRequestPeriodMetrics period)
    {
        return period.pullRequestsMerged() + period.pullRequestsClosedWithoutMerge();
    }

    private InsightSeverity getBacklogGrowingSeverity(long delta, double growthPercent)
    {
        if (growthPercent >= 50.0 && delta >= 10)
            return InsightSeverity.CRITICAL;

        if (growthPercent >= 25.0 && delta >= 5)
            return InsightSeverity.WARNING;

        if (growthPercent >= 10.0 && delta >= 2)
            return InsightSeverity.INFO;

        return null;
    }

    private InsightSeverity getMergeTimeHighSeverity(double currentMedian, long currentMerged)
    {
        if (currentMerged < 5)
            return null;

        if (currentMedian >= 168.0 && currentMerged >= 10)
            return InsightSeverity.CRITICAL;

        if (currentMedian >= 72.0)
            return InsightSeverity.WARNING;

        return null;
    }

    private InsightSeverity getMergeTimeIncreasedSeverity(double increasePercent,
                                                          double increaseHours,
                                                          double currentMedian,
                                                          long currentMerged,
                                                          long previousMerged)
    {
        if (currentMerged < 5 || previousMerged < 5)
            return null;

        if (increasePercent >= 100.0 && increaseHours >= 24.0 && currentMedian >= 72.0
                && currentMerged >= 10 && previousMerged >= 10)
        {
            return InsightSeverity.CRITICAL;
        }

        if (increasePercent >= 50.0 && increaseHours >= 12.0)
            return InsightSeverity.WARNING;

        if (increasePercent >= 25.0 && increaseHours >= 6.0)
            return InsightSeverity.INFO;

        return null;
    }

    private InsightSeverity getMergeRateLowSeverity(double currentMergeRatePercent, long currentCompleted)
    {
        if (currentCompleted < 10)
            return null;

        if (currentMergeRatePercent < 50.0 && currentCompleted >= 20)
            return InsightSeverity.CRITICAL;

        if (currentMergeRatePercent < 70.0)
            return InsightSeverity.WARNING;

        if (currentMergeRatePercent < 80.0)
            return InsightSeverity.INFO;

        return null;
    }

    private InsightSeverity getMergeRateDroppedSeverity(double percentagePointsDrop,
                                                        long currentCompleted,
                                                        long previousCompleted)
    {
        if (currentCompleted < 10 || previousCompleted < 10)
            return null;

        if (percentagePointsDrop >= 20.0 && currentCompleted >= 20 && previousCompleted >= 20)
            return InsightSeverity.CRITICAL;

        if (percentagePointsDrop >= 10.0)
            return InsightSeverity.WARNING;

        if (percentagePointsDrop >= 5.0)
            return InsightSeverity.INFO;

        return null;
    }

    private InsightSeverity getStalePullRequestRateSeverity(double staleRatePercent,
                                                            long stalePullRequests,
                                                            long openPullRequests)
    {
        if (openPullRequests < 5)
            return null;

        if (staleRatePercent >= 50.0 && stalePullRequests >= 5)
            return InsightSeverity.CRITICAL;

        if (staleRatePercent >= 35.0 && stalePullRequests >= 3)
            return InsightSeverity.WARNING;

        if (staleRatePercent >= 20.0 && stalePullRequests >= 2)
            return InsightSeverity.INFO;

        return null;
    }

    private InsightSeverity getMergeThroughputDroppedSeverity(double dropPercent,
                                                              long throughputDrop,
                                                              long previousMerged)
    {
        if (previousMerged < 10)
            return null;

        if (dropPercent >= 50.0 && throughputDrop >= 10)
            return InsightSeverity.CRITICAL;

        if (dropPercent >= 35.0 && throughputDrop >= 5)
            return InsightSeverity.WARNING;

        if (dropPercent >= 20.0 && throughputDrop >= 3)
            return InsightSeverity.INFO;

        return null;
    }

    private InsightSeverity getOversizedShareSeverity(double oversizedSharePercent,
                                                      long oversizedCompleted,
                                                      long totalCompleted)
    {
        if (totalCompleted < 30 || oversizedCompleted < 5)
            return null;

        if (oversizedSharePercent >= 30.0)
            return InsightSeverity.CRITICAL;

        if (oversizedSharePercent >= 20.0)
            return InsightSeverity.WARNING;

        if (oversizedSharePercent >= 10.0)
            return InsightSeverity.INFO;

        return null;
    }

    private InsightSeverity getOversizedMergeTimeSeverity(double ratio,
                                                          double differenceHours,
                                                          long oversizedMerged,
                                                          long nonOversizedMerged)
    {
        if (oversizedMerged < 10 || nonOversizedMerged < 10)
            return null;

        if (ratio >= 2.0 && differenceHours >= 72.0)
            return InsightSeverity.CRITICAL;

        if (ratio >= 1.5 && differenceHours >= 24.0)
            return InsightSeverity.WARNING;

        if (ratio >= 1.25 && differenceHours >= 12.0)
            return InsightSeverity.INFO;

        return null;
    }

    private InsightSeverity getOversizedMergeRateSeverity(double dropPercentagePoints,
                                                          long oversizedCompleted,
                                                          long nonOversizedCompleted)
    {
        if (oversizedCompleted < 20 || nonOversizedCompleted < 20)
            return null;

        if (dropPercentagePoints >= 30.0)
            return InsightSeverity.CRITICAL;

        if (dropPercentagePoints >= 20.0)
            return InsightSeverity.WARNING;

        if (dropPercentagePoints >= 10.0)
            return InsightSeverity.INFO;

        return null;
    }

    private InsightSeverity getSizeMergeTimeAssociationSeverity(double correlation, long sampleSize)
    {
        if (sampleSize < 30)
            return null;

        if (correlation >= 0.40)
            return InsightSeverity.WARNING;

        if (correlation >= 0.25)
            return InsightSeverity.INFO;

        return null;
    }

    private InsightSeverity getStaleOversizedOpenPullRequestsSeverity(long staleOversizedOpen,
                                                                      double staleRatePercent)
    {
        if (staleOversizedOpen >= 5 && staleRatePercent >= 50.0)
            return InsightSeverity.CRITICAL;

        if (staleOversizedOpen >= 3)
            return InsightSeverity.WARNING;

        if (staleOversizedOpen >= 2)
            return InsightSeverity.INFO;

        return null;
    }

    private record InsightContext(PullRequestPeriodMetrics currentPeriod,
                                  PullRequestPeriodMetrics previousPeriod,
                                  SizeInsightMetrics sizeMetrics) { }

    private record SizeInsightMetrics(long totalCompleted,
                                      long oversizedCompleted,
                                      long nonOversizedCompleted,

                                      long oversizedMerged,
                                      long nonOversizedMerged,

                                      long oversizedOpen,
                                      long staleOversizedOpen,

                                      Double oversizedMedianMergeTimeHours,
                                      Double nonOversizedMedianMergeTimeHours,
                                      Double oversizedToNonOversizedMedianMergeTimeRatio,

                                      long correlationSampleSize,
                                      Double changedLinesToMergeTimeCorrelation) { }
}