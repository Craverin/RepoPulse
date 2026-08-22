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
        long merged = 0, closedWithoutMerge = 0, nonDraftOpenAtPeriodEnd = 0, staleAtPeriodEnd = 0;

        Double mergeRatePercent, medianMergeTimeHours;

        List<Integer> mergeDurationSeconds = new ArrayList<>();

        for (PullRequestEntity pullRequest : pullRequests)
        {
            Instant closedAt = pullRequest.getClosedAt();

            if ((closedAt == null || !closedAt.isBefore(periodEndExclusive)
                    && pullRequest.getCreatedAt().isBefore(periodStart)
                    && !pullRequest.isDraft()))
            {
                nonDraftOpenAtPeriodEnd++;
                if (Duration.between(pullRequest.getUpdatedAt(), periodEndExclusive).toDays() > 30)
                    staleAtPeriodEnd++;
            }

            if (closedAt == null)
                continue;

            if (!closedAt.isBefore(periodStart) && closedAt.isBefore(periodEndExclusive))
            {
                if (pullRequest.getMergedAt() != null)
                {
                    merged++;
                    mergeDurationSeconds.add((int) Duration.between(
                            pullRequest.getCreatedAt(),
                            closedAt
                    ).toSeconds());

                    continue;
                }

                closedWithoutMerge++;
            }
        }

        long completed = merged + closedWithoutMerge;
        mergeRatePercent = completed == 0
                ? null
                : (double) merged / completed * 100;

        medianMergeTimeHours = mergeDurationSeconds.isEmpty()
                ? null
                : Statistics.median(mergeDurationSeconds) / 3600.0;

        return new PullRequestPeriodMetrics(
                periodStart,
                periodEndExclusive,

                pullRequests.size(),
                merged,
                closedWithoutMerge,

                nonDraftOpenAtPeriodEnd,
                staleAtPeriodEnd,

                Statistics.roundToHundredth(mergeRatePercent),
                Statistics.roundToHundredth(medianMergeTimeHours)
        );
    }
}
