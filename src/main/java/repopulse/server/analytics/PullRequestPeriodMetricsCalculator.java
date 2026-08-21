package repopulse.server.analytics;

import org.springframework.stereotype.Component;
import repopulse.server.analytics.statistics.Statistics;
import repopulse.server.dto.analytics.pullrequest.PullRequestPeriodMetrics;
import repopulse.server.entity.PullRequestEntity;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class PullRequestPeriodMetricsCalculator
{
    public PullRequestPeriodMetrics calculate(List<PullRequestEntity> pullRequests,
                                              Instant periodStart,
                                              Instant periodEndExclusive)
    {
        long pullRequestsMerged = 0, pullRequestsClosedWithoutMerge = 0, openPullRequestsAtPeriodEnd = 0;
        Double mergeRatePercent, medianMergeTimeHours;

        List<Integer> mergeDurationSeconds = new ArrayList<>();

        for (PullRequestEntity pullRequest : pullRequests)
        {
            Instant closedAt = pullRequest.getClosedAt();

            if (closedAt == null || !closedAt.isBefore(periodEndExclusive))
            {
                openPullRequestsAtPeriodEnd++;

                if (closedAt == null)
                    continue;
            }

            if (!closedAt.isBefore(periodStart) && closedAt.isBefore(periodEndExclusive))
            {
                if (pullRequest.getMergedAt() != null)
                {
                    pullRequestsMerged++;
                    mergeDurationSeconds.add((int) Duration.between(
                            pullRequest.getCreatedAt(),
                            closedAt
                    ).toSeconds());

                    continue;
                }

                pullRequestsClosedWithoutMerge++;
            }
        }

        long completed = pullRequestsMerged + pullRequestsClosedWithoutMerge;
        mergeRatePercent = completed == 0
                ? null
                : (double) pullRequestsMerged / completed * 100;

        medianMergeTimeHours = mergeDurationSeconds.isEmpty()
                ? null
                : Statistics.median(mergeDurationSeconds) / 3600.0;

        return new PullRequestPeriodMetrics(
                periodStart,
                periodEndExclusive,
                pullRequestsMerged,
                pullRequestsClosedWithoutMerge,
                openPullRequestsAtPeriodEnd,

                Statistics.roundToHundredth(mergeRatePercent),
                Statistics.roundToHundredth(medianMergeTimeHours)
        );
    }
}
