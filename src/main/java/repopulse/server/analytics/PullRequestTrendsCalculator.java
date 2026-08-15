package repopulse.server.analytics;

import org.springframework.stereotype.Component;
import repopulse.server.dto.analytics.pullrequest.PullRequestMonthlyMetrics;
import repopulse.server.entity.PullRequestEntity;

import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.*;

@Component
public class PullRequestTrendsCalculator
{
    private enum PullRequestMetricType
    {
        CREATED,
        OPEN_AT_MONTH_END,
        MERGED,
        CLOSED_WITHOUT_MERGE
    }


    public List<PullRequestMonthlyMetrics> calculate(List<PullRequestEntity> pullRequests, int months)
    {
        Map<YearMonth, Map<PullRequestMetricType, Long>> pullRequestMonthlyMetrics = new HashMap<>();

        YearMonth currentMonth = YearMonth.now(ZoneOffset.UTC);
        YearMonth startMonth = currentMonth.minusMonths(months);

        for (int i = 0; i < months; i++)
        {
            YearMonth month = startMonth.plusMonths(i);
            Map<PullRequestMetricType, Long> monthMetrics = new EnumMap<>(PullRequestMetricType.class);

            for (PullRequestMetricType type : PullRequestMetricType.values())
                monthMetrics.put(type, 0L);

            pullRequestMonthlyMetrics.put(month, monthMetrics);
        }

        Map<YearMonth, List<Long>> mergeDurationSeconds = new HashMap<>();
        for (int i = 0; i < months; i++)
            mergeDurationSeconds.put(startMonth.plusMonths(i), new ArrayList<>());

        long pullRequestsCreated = 0, pullRequestsMerged = 0, pullRequestsClosedWithoutMerge = 0,
             openPullRequestsAtMonthEnd = 0;

        YearMonth endMonth = YearMonth.now().minusMonths(1);

        for (PullRequestEntity pullRequest : pullRequests)
        {
            YearMonth createdAtMonth = toYearMonth(pullRequest.getCreatedAt());
            if (createdAtMonth.isAfter(endMonth))
                continue;

            incrementMetric(pullRequestMonthlyMetrics, createdAtMonth, PullRequestMetricType.CREATED);

            YearMonth closedAtMonth = null;

            if (pullRequest.getClosedAt() != null)
            {
                closedAtMonth = toYearMonth(pullRequest.getClosedAt());

                if (pullRequest.getMergedAt() != null)
                {
                    YearMonth mergedAtMonth = toYearMonth(pullRequest.getMergedAt());
                    incrementMetric(pullRequestMonthlyMetrics, mergedAtMonth, PullRequestMetricType.MERGED);

                    List<Long> durations = mergeDurationSeconds.get(mergedAtMonth);
                    if (durations != null)
                    {
                        durations.add(Duration.between(
                                pullRequest.getCreatedAt(),
                                pullRequest.getMergedAt()).toSeconds()
                        );
                    }
                }

                else
                    incrementMetric(pullRequestMonthlyMetrics, closedAtMonth, PullRequestMetricType.CLOSED_WITHOUT_MERGE);
            }

            updateOpenAtMonthEnd(
                    pullRequestMonthlyMetrics,
                    createdAtMonth,
                    closedAtMonth,
                    startMonth,
                    currentMonth
            );
        }

        List<PullRequestMonthlyMetrics> monthlyMetrics = new ArrayList<>();
        Double mergeRatePercent, medianMergeTimeHours;

        for (int i = 0; i < months; i++)
        {
            YearMonth currentYearMonth = startMonth.plusMonths(i);
            pullRequestsCreated = pullRequestMonthlyMetrics.get(startMonth.plusMonths(i))
                    .get(PullRequestMetricType.CREATED);

            pullRequestsMerged = pullRequestMonthlyMetrics.get(startMonth.plusMonths(i))
                    .get(PullRequestMetricType.MERGED);

            pullRequestsClosedWithoutMerge = pullRequestMonthlyMetrics.get(startMonth.plusMonths(i))
                    .get(PullRequestMetricType.CLOSED_WITHOUT_MERGE);

            openPullRequestsAtMonthEnd = pullRequestMonthlyMetrics.get(startMonth.plusMonths(i))
                    .get(PullRequestMetricType.OPEN_AT_MONTH_END);

            mergeRatePercent = PullRequestAnalyticsCalculator.roundToHundredth(
                    (double) pullRequestsMerged
                            / (pullRequestsMerged + pullRequestsClosedWithoutMerge) * 100
            );

            List<Long> durations = mergeDurationSeconds.get(currentYearMonth);
            medianMergeTimeHours = durations.isEmpty()
                    ? null
                    : PullRequestAnalyticsCalculator.roundToHundredth(
                            PullRequestAnalyticsCalculator.getMedianMergeTimeHours(durations)
            );

            monthlyMetrics.add(new PullRequestMonthlyMetrics(
                    currentYearMonth,
                    pullRequestsCreated,
                    pullRequestsMerged,
                    pullRequestsClosedWithoutMerge,
                    openPullRequestsAtMonthEnd,

                    mergeRatePercent,
                    medianMergeTimeHours
            ));
        }

        return monthlyMetrics;

    }

    private void incrementMetric(Map<YearMonth, Map<PullRequestMetricType, Long>> metrics,
                                 YearMonth month,
                                 PullRequestMetricType metricType)
    {
        Map<PullRequestMetricType, Long> monthMetrics = metrics.get(month);

        if (monthMetrics != null)
            metrics.get(month).merge(metricType, 1L, Long::sum);
    }

    private void updateOpenAtMonthEnd(Map<YearMonth, Map<PullRequestMetricType, Long>> metrics,
                                      YearMonth createdMonth,
                                      YearMonth closedMonth,
                                      YearMonth startMonth,
                                      YearMonth currentMonth)
    {
        YearMonth firstMonth = createdMonth.isBefore(startMonth) ? startMonth : createdMonth;
        YearMonth lastMonthExclusive = closedMonth == null || !closedMonth.isBefore(currentMonth)
                ? currentMonth
                : closedMonth;

        for (YearMonth month = firstMonth;
             month.isBefore(lastMonthExclusive);
             month = month.plusMonths(1)
        )
            incrementMetric(metrics, month, PullRequestMetricType.OPEN_AT_MONTH_END);
    }

    private YearMonth toYearMonth(Instant instant)
    {
        return YearMonth.from(instant.atZone(ZoneOffset.UTC));
    }
}
