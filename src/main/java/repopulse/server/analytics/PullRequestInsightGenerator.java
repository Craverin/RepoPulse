package repopulse.server.analytics;

import org.springframework.stereotype.Component;
import repopulse.server.dto.analytics.pullrequest.InsightSeverity;
import repopulse.server.dto.analytics.pullrequest.InsightValueUnit;
import repopulse.server.dto.analytics.pullrequest.PullRequestInsight;
import repopulse.server.dto.analytics.pullrequest.PullRequestInsightType;
import repopulse.server.dto.analytics.pullrequest.PullRequestPeriodMetrics;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class PullRequestInsightGenerator
{
    public List<PullRequestInsight> generate(PullRequestPeriodMetrics currentPeriod,
                                             PullRequestPeriodMetrics previousPeriod)
    {
        List<PullRequestInsight> insights = new ArrayList<>();

        // Backlog
        InsightSeverity backlogGrowingSeverity;
        long currentOpen = currentPeriod.openPullRequestsAtPeriodEnd();
        long previousOpen = previousPeriod.openPullRequestsAtPeriodEnd();

        long delta = currentOpen - previousOpen;
        double growthPercent = delta * 100.0 / Math.max(previousPeriod.openPullRequestsAtPeriodEnd(), 1);

        if ((backlogGrowingSeverity = getBacklogGrowingSeverity(delta, growthPercent)) != null)
        {
            String description = String.format(
                    Locale.ROOT,
                    "Open pull request backlog grew from %d to %d over the last 30 days" +
                    (previousOpen == 0 ? "." : " (%.1f%%)."),
                    previousOpen,
                    currentOpen,
                    growthPercent
            );

            insights.add(new PullRequestInsight(
                    PullRequestInsightType.BACKLOG_GROWING,
                    backlogGrowingSeverity,
                    description,
                    (double)currentOpen,
                    (double)previousOpen,
                    InsightValueUnit.COUNT
            ));
        }

        // Merge time
        InsightSeverity mergeTimeHighSeverity, mergeTimeIncreasedSeverity;

        if (currentPeriod.pullRequestsMerged() >= 5)
        {
            if ((mergeTimeHighSeverity = getMergeTimeHighSeverity(currentPeriod.medianMergeTimeHours(),
                    currentPeriod.pullRequestsMerged())) != null)
            {
                String description = String.format(
                        Locale.ROOT,
                        "Median merge time for pull requests merged in the last 30 days is %.1f hours," +
                                "exceeding the recommended 72-hour threshold.",
                        currentPeriod.medianMergeTimeHours()
                );

                insights.add(new PullRequestInsight(
                        PullRequestInsightType.MEDIAN_MERGE_TIME_HIGH,
                        mergeTimeHighSeverity,
                        description,
                        currentPeriod.medianMergeTimeHours(),
                        null,
                        InsightValueUnit.HOURS
                ));
            }

            if (previousPeriod.pullRequestsMerged() >= 5)
            {
                double increaseHours = currentPeriod.medianMergeTimeHours() - previousPeriod.medianMergeTimeHours();
                double increasePercent = increaseHours * 100.0 / previousPeriod.medianMergeTimeHours();

                if ((mergeTimeIncreasedSeverity = getMergeTimeIncreasedSeverity(increasePercent,
                        increaseHours, currentPeriod.medianMergeTimeHours(),
                        currentPeriod.pullRequestsMerged(), previousPeriod.pullRequestsMerged())) != null)
                {
                    String description = String.format(
                            Locale.ROOT,
                            "Median merge time increased from %.1f to %.1f hours compared " +
                                    "with the previous 30-day period (+%.1f%%).",
                            previousPeriod.medianMergeTimeHours(),
                            currentPeriod.medianMergeTimeHours(),
                            increasePercent
                    );

                    insights.add(new PullRequestInsight(
                            PullRequestInsightType.MEDIAN_MERGE_TIME_INCREASED,
                            mergeTimeIncreasedSeverity,
                            description,
                            currentPeriod.medianMergeTimeHours(),
                            previousPeriod.medianMergeTimeHours(),
                            InsightValueUnit.HOURS
                    ));
                }
            }
        }

        // Merge rate
        InsightSeverity mergeRateLowSeverity, mergeRateDroppedSeverity;
        long currentCompleted = currentPeriod.pullRequestsMerged() + currentPeriod.pullRequestsClosedWithoutMerge();

        if (currentPeriod.mergeRatePercent() != null && currentCompleted >= 10)
        {
            if ((mergeRateLowSeverity = getMergeRateLowSeverity(currentPeriod.mergeRatePercent(),
                    currentCompleted)) != null)
            {
                String description = String.format(
                        Locale.ROOT,
                        "Merge rate for the last 30 days is %.1f%%, below the recommended 80%% threshold: " +
                        "%d of %d completed pull requests were merged.",
                        currentPeriod.mergeRatePercent(),
                        currentPeriod.pullRequestsMerged(),
                        currentCompleted
                );

                insights.add(new PullRequestInsight(
                        PullRequestInsightType.MERGE_RATE_LOW,
                        mergeRateLowSeverity,
                        description,
                        currentPeriod.mergeRatePercent(),
                        null,
                        InsightValueUnit.PERCENT
                ));
            }

            long previousCompleted = previousPeriod.pullRequestsMerged() + previousPeriod.pullRequestsClosedWithoutMerge();

            if (previousPeriod.mergeRatePercent() != null && previousCompleted >= 10)
            {
                double dropPercentagePoints = previousPeriod.mergeRatePercent() - currentPeriod.mergeRatePercent();

                if ((mergeRateDroppedSeverity = getMergeRateDroppedSeverity(dropPercentagePoints,
                        currentCompleted, previousCompleted)) != null)
                {
                    String description = String.format(
                            Locale.ROOT,
                            "Merge rate dropped from %.1f%% to %.1f%% compared with the previous " +
                                    "30-day period (-%.1f percentage points).",
                            previousPeriod.mergeRatePercent(),
                            currentPeriod.mergeRatePercent(),
                            dropPercentagePoints
                    );

                    insights.add(new PullRequestInsight(
                            PullRequestInsightType.MERGE_RATE_DROPPED,
                            mergeRateDroppedSeverity,
                            description,
                            currentPeriod.mergeRatePercent(),
                            previousPeriod.mergeRatePercent(),
                            InsightValueUnit.PERCENT
                    ));
                }
            }
        }

        return insights;
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
        if (increasePercent >= 100.0 && increaseHours >= 24 && currentMedian >= 72.0 &&
                currentMerged >= 10 && previousMerged >= 10)
            return InsightSeverity.CRITICAL;
        if (increasePercent >= 50.0 && increaseHours >= 12)
            return InsightSeverity.WARNING;
        if (increasePercent >= 25.0 && increaseHours >= 6)
            return InsightSeverity.INFO;

        return null;
    }

    private InsightSeverity getMergeRateLowSeverity(double currentMergeRatePercent, long currentCompleted)
    {
        if (currentMergeRatePercent < 50.0 && currentCompleted >= 20)
            return InsightSeverity.CRITICAL;
        if (currentMergeRatePercent < 70.0)
            return InsightSeverity.WARNING;
        if (currentMergeRatePercent < 80.0)
            return InsightSeverity.INFO;

        return null;
    }

    private InsightSeverity getMergeRateDroppedSeverity(double dropPercentagePoints,
                                                        long currentCompleted,
                                                        long previousCompleted)
    {
        if (dropPercentagePoints >= 20.0 && currentCompleted >= 20 && previousCompleted >= 20)
            return InsightSeverity.CRITICAL;
        else if (dropPercentagePoints >= 10)
            return InsightSeverity.WARNING;
        else if (dropPercentagePoints >= 5)
            return InsightSeverity.INFO;

        return null;
    }
}
