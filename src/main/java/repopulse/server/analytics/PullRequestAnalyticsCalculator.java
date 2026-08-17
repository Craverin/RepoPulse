package repopulse.server.analytics;

import org.springframework.stereotype.Component;
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
        List<Long> mergeDurationSeconds = new ArrayList<>();

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

                long mergeTimeSeconds = Duration.between(
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
            staleOpenPullRequestRatePercent = roundToHundredth(
                    (staleOpenPullRequests + veryStaleOpenPullRequests)
                            / (double)openPullRequests * 100
            );
        }

        long closedPullRequests = mergedPullRequests + closedWithoutMergePullRequests;
        if (closedPullRequests > 0)
            mergeRatePercent = roundToHundredth(mergedPullRequests / (double)closedPullRequests * 100);

        if (mergedPullRequests > 0)
        {
            averageMergeTimeHours = roundToHundredth(totalMergeTimeSeconds / mergedPullRequests / 3600d);
            medianMergeTimeHours = roundToHundredth(getMedianMergeTimeHours(mergeDurationSeconds));
        }

        return new PullRequestAnalytics(
                totalPullRequests,
                openPullRequests,
                openDraftPullRequests,
                mergedPullRequests,
                closedWithoutMergePullRequests,

                mergeRatePercent,
                averageMergeTimeHours,
                medianMergeTimeHours,

                freshOpenPullRequests,
                agingOpenPullRequests,
                staleOpenPullRequests,
                veryStaleOpenPullRequests,
                staleOpenPullRequestRatePercent,

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

    public static Double getMedianMergeTimeHours(List<Long> mergeDurationSeconds)
    {
        Collections.sort(mergeDurationSeconds);
        int size = mergeDurationSeconds.size();

        if (size == 0)
            return null;

        if (size % 2 != 0)
        {
            long medianSeconds = mergeDurationSeconds.get(size / 2);
            return medianSeconds / 3600d;
        }

        double medianSeconds =
                (mergeDurationSeconds.get(size / 2 - 1)
                        + mergeDurationSeconds.get(size / 2)) / 2d;

        return medianSeconds / 3600;
    }


    public static Double roundToHundredth(Double number)
    {
        double scale = Math.pow(10, 2);
        return Math.round(number * scale) / scale;
    }
}
