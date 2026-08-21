package repopulse.server.analytics;

import org.springframework.stereotype.Component;
import repopulse.server.analytics.statistics.Statistics;
import repopulse.server.dto.analytics.pullrequest.PullRequestAnalytics;
import repopulse.server.dto.StalePullRequest;
import repopulse.server.entity.PullRequestEntity;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Component
public class PullRequestAnalyticsCalculator
{
    public PullRequestAnalytics calculate(List<PullRequestEntity> pullRequests)
    {
        long totalPullRequests = pullRequests.size();

        long openPullRequests = 0, openDraftPullRequests = 0, mergedPullRequests = 0,
             closedWithoutMergePullRequests = 0;

        long freshOpenPullRequests = 0, agingOpenPullRequests = 0, staleOpenPullRequests = 0,
             veryStaleOpenPullRequests = 0;

        long createdLast30Days = 0, mergedLast30Days = 0, closedWithoutMergeLast30Days = 0;

        Set<String> pullRequestAuthors = new HashSet<>();

        long totalMergeTimeSeconds = 0;
        List<Integer> mergeDurationSeconds = new ArrayList<>();

        Instant thirtyDaysAgo = Instant.now().minus(Duration.ofDays(30));

        for (PullRequestEntity pullRequestEntity : pullRequests)
        {
            if (pullRequestEntity.getAuthorLogin() != null)
                pullRequestAuthors.add(pullRequestEntity.getAuthorLogin());

            if (pullRequestEntity.getCreatedAt().isAfter(thirtyDaysAgo))
                createdLast30Days++;

            if (pullRequestEntity.getState().equals("OPEN"))
            {
                openPullRequests++;
                if (pullRequestEntity.isDraft())
                    openDraftPullRequests++;

                long inactiveDays = Duration.between(pullRequestEntity.getUpdatedAt(), Instant.now()).toDays();

                if (inactiveDays <= 7)
                    freshOpenPullRequests++;
                else if (inactiveDays <= 30)
                    agingOpenPullRequests++;
                else if (inactiveDays <= 90)
                    staleOpenPullRequests++;
                else
                    veryStaleOpenPullRequests++;
            }

            if (pullRequestEntity.getState().equals("CLOSED"))
            {
                closedWithoutMergePullRequests++;
                if (pullRequestEntity.getClosedAt().isAfter(thirtyDaysAgo))
                    closedWithoutMergeLast30Days++;
            }

            if (pullRequestEntity.getState().equals("MERGED"))
            {
                mergedPullRequests++;

                if (pullRequestEntity.getMergedAt().isAfter(thirtyDaysAgo))
                    mergedLast30Days++;

                int mergeTimeSeconds = (int) Duration.between(
                        pullRequestEntity.getCreatedAt(),
                        pullRequestEntity.getMergedAt()
                ).toSeconds();

                mergeDurationSeconds.add(mergeTimeSeconds);
                totalMergeTimeSeconds += mergeTimeSeconds;
            }
        }

        List<StalePullRequest> stalestPullRequests = getStalestPullRequests(pullRequests);

        Double staleOpenPullRequestRatePercent = null;
        Double mergeRatePercent = null, averageMergeTimeHours = null, medianMergeTimeHours = null;
        if (openPullRequests > 0)
        {
            staleOpenPullRequestRatePercent =
                    (staleOpenPullRequests + veryStaleOpenPullRequests)
                     / (double)openPullRequests * 100;
        }

        long closedPullRequests = mergedPullRequests + closedWithoutMergePullRequests;
        if (closedPullRequests > 0)
            mergeRatePercent = mergedPullRequests / (double)closedPullRequests * 100;

        if (mergedPullRequests > 0)
        {
            averageMergeTimeHours = (double)totalMergeTimeSeconds / mergedPullRequests / 3600;
            medianMergeTimeHours = Statistics.median(mergeDurationSeconds) / 3600.0;
        }

        return new PullRequestAnalytics(
                totalPullRequests,
                openPullRequests,
                openDraftPullRequests,
                mergedPullRequests,
                closedWithoutMergePullRequests,

                Statistics.roundToHundredth(mergeRatePercent),
                Statistics.roundToHundredth(averageMergeTimeHours),
                Statistics.roundToHundredth(medianMergeTimeHours),

                freshOpenPullRequests,
                agingOpenPullRequests,
                staleOpenPullRequests,
                veryStaleOpenPullRequests,
                Statistics.roundToHundredth(staleOpenPullRequestRatePercent),

                createdLast30Days,
                mergedLast30Days,
                closedWithoutMergeLast30Days,
                pullRequestAuthors.size(),

                stalestPullRequests
        );

    }

    private List<StalePullRequest> getStalestPullRequests(List<PullRequestEntity> pullRequests)
    {
        return pullRequests
                .stream()
                .filter(pr -> pr.getState().equals("OPEN"))
                .map(pr -> new StalePullRequest(
                        pr.getNumber(),
                        pr.getTitle(),
                        pr.getHtmlUrl(),
                        pr.getAuthorLogin(),
                        Duration.between(pr.getUpdatedAt(), Instant.now()).toDays()
                ))
                .sorted(Comparator.comparingLong(StalePullRequest::inactiveDays).reversed())
                .limit(5)
                .toList();
    }
}
