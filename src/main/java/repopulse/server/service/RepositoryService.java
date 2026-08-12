package repopulse.server.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import repopulse.server.api.GithubApiClient;
import repopulse.server.dto.BaseRepositoryInfo;
import repopulse.server.dto.GithubPullRequestResponse;
import repopulse.server.dto.GithubRepositoryResponse;
import repopulse.server.dto.analytics.RepositoryAnalytics;
import repopulse.server.dto.analytics.RepositoryAnalyticsResponse;
import repopulse.server.dto.analytics.StalePullRequestResponse;
import repopulse.server.entity.PullRequestEntity;
import repopulse.server.entity.RepositoryEntity;
import repopulse.server.repository.PullRequestRepository;
import repopulse.server.repository.RepositoryRepository;
import tools.jackson.databind.annotation.JsonSerialize;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Service
public class RepositoryService
{
    private final GithubApiClient githubClient;
    private final RepositoryRepository repositoryRepository;
    private final PullRequestRepository pullRequestRepository;

    public RepositoryService(GithubApiClient githubClient,
                             RepositoryRepository repositoryRepository,
                             PullRequestRepository pullRequestRepository)
    {
        this.githubClient = githubClient;
        this.repositoryRepository = repositoryRepository;
        this.pullRequestRepository = pullRequestRepository;
    }

    @Transactional
    public RepositoryAnalyticsResponse getRepositoryAnalytics(String url)
    {
        BaseRepositoryInfo repositoryInfo = parseRepositoryUrl(url);
        String owner = repositoryInfo.owner();
        String repositoryName = repositoryInfo.name();

        RepositoryEntity repositoryEntity = loadRepository(owner, repositoryName);
        List<PullRequestEntity> pullRequestEntities = loadPullRequests(owner, repositoryName, repositoryEntity);

        long totalPullRequests = pullRequestEntities.size();

        long openPullRequests = 0, openDraftPullRequests = 0, mergedPullRequests = 0,
             mergedDraftPullRequests = 0, closedWithoutMergePullRequests = 0;

        long freshOpenPullRequests = 0, agingOpenPullRequests = 0, staleOpenPullRequests = 0,
             veryStaleOpenPullRequests = 0;

        long createdLast30Days = 0, mergedLast30Days = 0, closedWithoutMergeLast30Days = 0;

        long uniquePullRequestAuthors = 0;
        List<String> pullRequestAuthors = new ArrayList<>();

        long totalMergeTimeSeconds = 0;
        List<Long> mergeDurationSeconds = new ArrayList<>();

        for (PullRequestEntity pullRequestEntity : pullRequestEntities)
        {
            long daysSinceCreation = Duration.between(
                    pullRequestEntity.getCreatedAt(),
                    Instant.now()
            ).toDays();

            if (daysSinceCreation <= 30)
                createdLast30Days++;

            if (pullRequestEntity.getState().equals("open"))
            {
                openPullRequests++;
                if (pullRequestEntity.isDraft())
                    openDraftPullRequests++;

                long daysWithoutUpdate = Duration.between(
                        pullRequestEntity.getUpdatedAt(),
                        Instant.now()
                ).toDays();

                if (daysWithoutUpdate <= 7)
                    freshOpenPullRequests++;
                else if (daysWithoutUpdate <= 30)
                    agingOpenPullRequests++;
                else if (daysWithoutUpdate <= 90)
                    staleOpenPullRequests++;
                else
                    staleOpenPullRequests++;
            }

            if (pullRequestEntity.getState().equals("closed"))
            {
                if (pullRequestEntity.getMergedAt() != null)
                {
                    mergedPullRequests++;
                    if (pullRequestEntity.isDraft())
                        mergedDraftPullRequests++;

                    if (daysSinceCreation <= 30)
                        mergedLast30Days++;

                    long mergeTimeSeconds = Duration.between(
                            pullRequestEntity.getCreatedAt(),
                            pullRequestEntity.getMergedAt()
                    ).toSeconds();

                    mergeDurationSeconds.add(mergeTimeSeconds);
                    totalMergeTimeSeconds += mergeTimeSeconds;

                    continue;
                }

                closedWithoutMergePullRequests++;
                if (daysSinceCreation <= 30)
                    closedWithoutMergeLast30Days++;
            }

            if (!pullRequestAuthors.contains(pullRequestEntity.getAuthorLogin()))
            {
                uniquePullRequestAuthors++;
                pullRequestAuthors.add(pullRequestEntity.getAuthorLogin());
            }
        }

        Double staleOpenPullRequestRatePercent = null;
        Double mergeRatePercent = null, averageMergeTimeHours = null, medianMergeTimeHours = null;
        if (totalPullRequests > 0)
        {
            staleOpenPullRequestRatePercent =
                    (staleOpenPullRequests + veryStaleOpenPullRequests) / (double) openPullRequests;

            long closedPullRequests = mergedPullRequests + closedWithoutMergePullRequests;
            mergeRatePercent = roundTo((double) mergedPullRequests / closedPullRequests * 100d, 2);
            averageMergeTimeHours = roundTo(totalMergeTimeSeconds / mergedPullRequests / 3600d, 2);
            medianMergeTimeHours = roundTo(getMedianMergeTimeHours(mergeDurationSeconds), 2);
        }

        List<StalePullRequestResponse> stalestPullRequests = getStalestPullRequests(pullRequestEntities);

        RepositoryAnalytics analytics = new RepositoryAnalytics(
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
                uniquePullRequestAuthors,

                stalestPullRequests
        );

        return new RepositoryAnalyticsResponse(
                repositoryEntity.getId(),
                repositoryEntity.getOwner(),
                repositoryEntity.getName(),
                repositoryEntity.getHtmlUrl(),
                repositoryEntity.getLastSyncedAt(),
                analytics
        );
    }

    public BaseRepositoryInfo parseRepositoryUrl(String repositoryUrl)
    {
        if (repositoryUrl == null || repositoryUrl.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Repository URL should not be empty");

        URI uri;

        try
        {
            uri = URI.create(repositoryUrl.trim());
        }
        catch (IllegalArgumentException exception)
        {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid repository URL");
        }

        if (!uri.getHost().equalsIgnoreCase("github.com"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expected a GitHub repository URL");

        List<String> pathParts = Arrays.stream(uri.getPath().split("/"))
                .filter(p -> !p.isEmpty())
                .toList();
        if (pathParts.size() != 2)

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Expected URL in format https://github.com/{owner}/{repository}"
            );


        String owner = pathParts.get(0);
        String repositoryName = pathParts.get(1);

        if (repositoryName.endsWith(".git"))
            repositoryName = repositoryName.substring(0, repositoryName.length() - 4);

        return new BaseRepositoryInfo(owner, repositoryName);
    }

    private List<StalePullRequestResponse> getStalestPullRequests(List<PullRequestEntity> pullRequestEntities)
    {
        List<StalePullRequestResponse> stalestPullRequests = new ArrayList<>();

        int pullRequestNumber = pullRequestEntities.size();
        int startIndex = pullRequestNumber > 5 ? pullRequestNumber - 5 : 0;
        int endIndex = pullRequestNumber - 1;

        for (PullRequestEntity pullRequest : pullRequestEntities.subList(startIndex, endIndex))
        {
            stalestPullRequests.add(new StalePullRequestResponse(
                    pullRequest.getNumber(),
                    pullRequest.getTitle(),
                    pullRequest.getHtmlUrl(),
                    pullRequest.getAuthorLogin(),
                    Duration.between(pullRequest.getUpdatedAt(), Instant.now()).toDays()
            ));
        }

        return stalestPullRequests;
    }

    private double getMedianMergeTimeHours(List<Long> mergeDurationSeconds)
    {
        Collections.sort(mergeDurationSeconds);
        int size = mergeDurationSeconds.size();

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

    private RepositoryEntity loadRepository(String owner, String repositoryName)
    {
        GithubRepositoryResponse resp = githubClient.getRepository(owner, repositoryName);
        RepositoryEntity currentEntity = repositoryRepository.findByGithubId(resp.id()).orElse(null);

        if (currentEntity == null)
        {
            RepositoryEntity repositoryEntity = new RepositoryEntity(
                    resp.id(),
                    resp.owner().login(),
                    resp.name(),
                    resp.htmlUrl(),
                    resp.defaultBranch()
            );

            repositoryRepository.save(repositoryEntity);
            return repositoryEntity;
        }

        currentEntity.setOwner(resp.owner().login());
        currentEntity.setName(resp.name());
        currentEntity.setHtmlUrl(resp.htmlUrl());
        currentEntity.setDefaultBranch(resp.defaultBranch());

        return currentEntity;
    }

    private List<PullRequestEntity> loadPullRequests(String owner, String repositoryName, RepositoryEntity repositoryEntity)
    {
        List<GithubPullRequestResponse> pullRequests = githubClient.getPullRequests(owner, repositoryName, 1);
        List<PullRequestEntity> pullRequestEntities = new ArrayList<>();

        for (GithubPullRequestResponse pullRequest : pullRequests)
        {
            PullRequestEntity currentEntity = pullRequestRepository.findByGithubId(pullRequest.id())
                                                                   .orElse(null);

            if (currentEntity == null)
            {
                PullRequestEntity pullRequestEntity = new PullRequestEntity(
                        pullRequest.id(),
                        repositoryEntity,
                        pullRequest.number(),
                        pullRequest.state(),
                        pullRequest.title(),
                        pullRequest.htmlUrl(),
                        pullRequest.user().login(),
                        pullRequest.draft(),
                        pullRequest.updatedAt(),
                        pullRequest.createdAt(),
                        pullRequest.closedAt(),
                        pullRequest.mergedAt()
                );

                pullRequestEntities.add(pullRequestEntity);
                pullRequestRepository.save(pullRequestEntity);
                continue;
            }

            currentEntity.setState(pullRequest.state());
            currentEntity.setTitle(pullRequest.title());
            currentEntity.setHtmlUrl(pullRequest.htmlUrl());
            currentEntity.setAuthorLogin(pullRequest.user().login());
            currentEntity.setDraft(pullRequest.draft());
            currentEntity.setUpdatedAt(pullRequest.updatedAt());
            currentEntity.setClosedAt(pullRequest.closedAt());
            currentEntity.setMergedAt(pullRequest.mergedAt());

            pullRequestEntities.add(currentEntity);
        }

        repositoryEntity.setLastSyncedAt(Instant.now());
        return pullRequestEntities;
    }

    private Double roundTo(Double number, int places)
    {
        double scale = Math.pow(10, places);
        return Math.round(number * scale) / scale;
    }
}
