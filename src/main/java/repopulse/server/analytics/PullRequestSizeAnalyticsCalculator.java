package repopulse.server.analytics;

import org.springframework.stereotype.Component;
import repopulse.server.analytics.statistics.Statistics;
import repopulse.server.dto.analytics.pullrequest.size.*;
import repopulse.server.entity.PullRequestEntity;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

@Component
public class PullRequestSizeAnalyticsCalculator
{
    public PullRequestSizeAnalytics calculate(List<PullRequestEntity> pullRequests)
    {
        int completedPullRequests = 0, mergedPullRequests = 0;

        Integer p90ChangedLines, p90ChangeFiles;

        List<Integer> changedLinesCount = new ArrayList<>();
        List<Integer> changedFilesCount = new ArrayList<>();

        List<Integer> mergedChangedLines = new ArrayList<>();
        List<Integer> mergedChangedFiles = new ArrayList<>();

        List<Integer> medianMergeTimeSeconds = new ArrayList<>();

        Map<PullRequestSizeCategory, CategoryStatistics> categoryMetrics =
                new EnumMap<>(PullRequestSizeCategory.class);

        for (PullRequestSizeCategory category : PullRequestSizeCategory.values())
            categoryMetrics.put(category, new CategoryStatistics());

        for (PullRequestEntity pullRequest : pullRequests)
        {
            PullRequestSizeCategory sizeCategory = PullRequestSizeCategory.getSizeCategory(
                    pullRequest.getChangedLines()
            );

            CategoryStatistics categoryStatistics = categoryMetrics.get(sizeCategory);

            if (pullRequest.getClosedAt() != null)
            {
                completedPullRequests++;

                changedLinesCount.add(pullRequest.getChangedLines());
                changedFilesCount.add(pullRequest.getChangedFiles());

                if (pullRequest.getMergedAt() != null)
                {
                    mergedPullRequests++;
                    mergedChangedLines.add(pullRequest.getChangedLines());
                    mergedChangedFiles.add(pullRequest.getChangedFiles());

                    int mergeDuration = (int) Duration.between(
                            pullRequest.getCreatedAt(),
                            pullRequest.getMergedAt()
                    ).toSeconds();

                    categoryStatistics.addMerged(mergeDuration);
                    medianMergeTimeSeconds.add(mergeDuration);

                    continue;
                }

                categoryStatistics.addClosedWithoutMerge();
                continue;
            }

            if (!pullRequest.isDraft())
            {
                boolean isStale = Duration.between(
                        pullRequest.getCreatedAt(),
                        Instant.now()
                ).toDays() > 30;

                categoryStatistics.addOpen(isStale);
            }
        }


        Double oversizedMedianMergeTimeHours = null, nonOversizedMedianMergeTimeHours = null,
               oversizedToNonOversizedMedianMergeTimeRatio = null, changedLinesToMergeTimeCorrelation = null,
               changedFilesToMergeTimeCorrelation = null;

        Double medianChangedLines = Statistics.median(changedLinesCount);
        Double medianChangedFiles = Statistics.median(changedFilesCount);

        p90ChangedLines = Statistics.percentile(changedLinesCount, 0.9);
        p90ChangeFiles = Statistics.percentile(changedFilesCount, 0.9);

        int totalCompleted = completedPullRequests;

        List<Integer> nonOversizedMergeDurations = Stream.of(
                    PullRequestSizeCategory.SMALL,
                    PullRequestSizeCategory.MEDIUM
                ).flatMap(category ->
                        categoryMetrics.get(category).mergeDurationsSeconds.stream()
                ).toList();

        List<Integer> oversizedMergeDurations = Stream.of(
                        PullRequestSizeCategory.LARGE,
                        PullRequestSizeCategory.ENORMOUS
                ).flatMap(category ->
                        categoryMetrics.get(category).mergeDurationsSeconds.stream()
                ).toList();

        if (nonOversizedMergeDurations.size() >= 10 && oversizedMergeDurations.size() >= 10)
        {
            double nonOversizedMedianMergeTime = Statistics.median(nonOversizedMergeDurations);
            double oversizedMedianMergeTime = Statistics.median(oversizedMergeDurations);

            oversizedToNonOversizedMedianMergeTimeRatio =
                            oversizedMedianMergeTime / nonOversizedMedianMergeTime;

            nonOversizedMedianMergeTimeHours = nonOversizedMedianMergeTime / 3600;
            oversizedMedianMergeTimeHours = oversizedMedianMergeTime / 3600;
        }

        if (mergedPullRequests >= 30)
        {
            changedLinesToMergeTimeCorrelation = Statistics.spearmanCorrelation(
                    mergedChangedLines,
                    medianMergeTimeSeconds
            );

            changedFilesToMergeTimeCorrelation = Statistics.spearmanCorrelation(
                    mergedChangedFiles,
                    medianMergeTimeSeconds
            );
        }



        PullRequestSizeStatistics sizeStatistics = new PullRequestSizeStatistics(
                completedPullRequests,
                medianChangedLines,
                medianChangedFiles,
                p90ChangedLines,
                p90ChangeFiles
        );

        List<PullRequestSizeCategoryMetrics> sizeCategoryMetrics =
                Arrays.stream(PullRequestSizeCategory.values())
                        .map(category -> createCategoryMetrics(
                                category,
                                categoryMetrics.get(category),
                                totalCompleted))
                        .toList();

        PullRequestSizeImpact sizeImpact = new PullRequestSizeImpact(
                Statistics.roundToHundredth(oversizedMedianMergeTimeHours),
                Statistics.roundToHundredth(nonOversizedMedianMergeTimeHours),
                Statistics.roundToHundredth(oversizedToNonOversizedMedianMergeTimeRatio),
                mergedPullRequests,
                Statistics.roundToHundredth(changedLinesToMergeTimeCorrelation),
                Statistics.roundToHundredth(changedFilesToMergeTimeCorrelation)
        );

        List<PullRequestEntity> oversizedOpenPullRequestEntities = pullRequests.stream()
                .filter(pr -> pr.getState().equals("OPEN"))
                .filter(pr -> pr.getAdditions() + pr.getDeletions() >= 500)
                .sorted(Comparator.comparingInt((PullRequestEntity pr) ->
                                pr.getAdditions() + pr.getDeletions())
                        .reversed()
                        .thenComparing(PullRequestEntity::getCreatedAt))
                .limit(10)
                .toList();

        List<OversizedOpenPullRequest> oversizedOpenPullRequests =
                oversizedOpenPullRequestEntities.stream()
                        .map(pr -> new OversizedOpenPullRequest(
                                pr.getNumber(),
                                pr.getTitle(),
                                pr.getHtmlUrl(),
                                pr.getAuthorLogin(),
                                pr.isDraft(),
                                pr.getChangedLines(),
                                pr.getChangedFiles(),
                                (int) Duration.between(pr.getCreatedAt(), Instant.now()).toDays(),
                                (int) Duration.between(pr.getUpdatedAt(), Instant.now()).toDays()
                        ))
                        .toList();


        return new PullRequestSizeAnalytics(
                sizeStatistics,
                sizeCategoryMetrics,
                sizeImpact,
                oversizedOpenPullRequests
        );
    }

    private PullRequestSizeCategoryMetrics createCategoryMetrics(PullRequestSizeCategory category,
                                                                 CategoryStatistics categoryStatistics,
                                                                 int totalCompleted)
    {
        int completed = categoryStatistics.mergedPullRequests
                + categoryStatistics.closedWithoutMergePullRequests;

        Double completedSharePercent = totalCompleted == 0
                ? null
                : completed * 100.0 / totalCompleted;

        Double mergeRatePercent = completed == 0
                ? null
                : categoryStatistics.mergedPullRequests * 100.0 / completed;

        Double staleRatePercent = categoryStatistics.openPullRequests == 0
                ? null
                : categoryStatistics.staleOpenPullRequests * 100.0 / categoryStatistics.openPullRequests;

        Double medianMergeTimeHours = categoryStatistics.mergeDurationsSeconds.isEmpty()
                ? null
                : Statistics.median(categoryStatistics.mergeDurationsSeconds) / 3600.0;

        return new PullRequestSizeCategoryMetrics(
                category,
                completed,
                Statistics.roundToHundredth(completedSharePercent),
                categoryStatistics.mergedPullRequests,
                categoryStatistics.closedWithoutMergePullRequests,
                Statistics.roundToHundredth(mergeRatePercent),
                Statistics.roundToHundredth(medianMergeTimeHours),
                categoryStatistics.openPullRequests,
                categoryStatistics.staleOpenPullRequests,
                Statistics.roundToHundredth(staleRatePercent)
        );
    }

    private static final class CategoryStatistics
    {
        private int mergedPullRequests;
        private int closedWithoutMergePullRequests;

        private int openPullRequests;
        private int staleOpenPullRequests;

        private final List<Integer> mergeDurationsSeconds = new ArrayList<>();

        public void addMerged(int mergeDurationSeconds)
        {
            mergedPullRequests++;
            mergeDurationsSeconds.add(mergeDurationSeconds);
        }

        public void addClosedWithoutMerge()
        {
            closedWithoutMergePullRequests++;
        }

        public void addOpen(boolean isStale)
        {
            openPullRequests++;

            if (isStale)
                staleOpenPullRequests++;
        }
    }
}

