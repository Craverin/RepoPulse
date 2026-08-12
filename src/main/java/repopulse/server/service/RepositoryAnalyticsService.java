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

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class RepositoryAnalyticsService
{
    private final GithubApiClient githubClient;
    private final RepositoryRepository repositoryRepository;
    private final PullRequestRepository pullRequestRepository;

    public RepositoryAnalyticsService(GithubApiClient githubClient,
                                      RepositoryRepository repositoryRepository,
                                      PullRequestRepository pullRequestRepository)
    {
        this.githubClient = githubClient;
        this.repositoryRepository = repositoryRepository;
        this.pullRequestRepository = pullRequestRepository;
    }

    @Transactional
    public RepositoryAnalyticsResponse analyzeRepository(String url)
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

        long uniquePullRequestAuthors;
        Set<String> pullRequestAuthors = new HashSet<>();

        long totalMergeTimeSeconds = 0;
        List<Long> mergeDurationSeconds = new ArrayList<>();

        Instant thirtyDaysAgo = Instant.now().minus(Duration.ofDays(30));

        for (PullRequestEntity pullRequestEntity : pullRequestEntities) {
            pullRequestAuthors.add(pullRequestEntity.getAuthorLogin());

            if (pullRequestEntity.getCreatedAt().isAfter(thirtyDaysAgo))
                createdLast30Days++;

            if (pullRequestEntity.getState().equals("open")) {
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

            if (pullRequestEntity.getState().equals("closed")) {
                if (pullRequestEntity.getMergedAt() != null) {
                    mergedPullRequests++;
                    if (pullRequestEntity.isDraft())
                        mergedDraftPullRequests++;

                    if (pullRequestEntity.getMergedAt().isAfter(thirtyDaysAgo))
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
                if (pullRequestEntity.getClosedAt().isAfter(thirtyDaysAgo))
                    closedWithoutMergeLast30Days++;
            }
        }

        List<StalePullRequestResponse> stalestPullRequests = pullRequestEntities
                .stream()
                .filter(pr -> pr.getState().equals("open"))
                .map(pr -> new StalePullRequestResponse(
                        pr.getNumber(),
                        pr.getTitle(),
                        pr.getHtmlUrl(),
                        pr.getAuthorLogin(),
                        Duration.between(pr.getUpdatedAt(), Instant.now()).toDays()
                ))
                .sorted(Comparator.comparingLong(StalePullRequestResponse::inactiveDays).reversed())
                .limit(5)
                .toList();

        uniquePullRequestAuthors = pullRequestAuthors.size();

        Double staleOpenPullRequestRatePercent = null;
        Double mergeRatePercent = null, averageMergeTimeHours = null, medianMergeTimeHours = null;
        if (openPullRequests > 0)
        {
            staleOpenPullRequestRatePercent = roundToHundredths(
                            (staleOpenPullRequests + veryStaleOpenPullRequests)
                                    / (double)openPullRequests * 100
            );
        }

        long closedPullRequests = mergedPullRequests + closedWithoutMergePullRequests;
        if (closedPullRequests > 0)
            mergeRatePercent = roundToHundredths(mergedPullRequests / (double)closedPullRequests * 100);

        if (mergedPullRequests > 0)
        {
            averageMergeTimeHours = roundToHundredths(totalMergeTimeSeconds / mergedPullRequests / 3600d);
            medianMergeTimeHours = roundToHundredths(getMedianMergeTimeHours(mergeDurationSeconds));
        }

        RepositoryAnalytics analytics = new RepositoryAnalytics(
                totalPullRequests,
                openPullRequests,
                openDraftPullRequests,
                mergedPullRequests,
                mergedDraftPullRequests,
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
        List<GithubPullRequestResponse> pullRequests = githubClient.getAllPullRequests(owner, repositoryName);
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

    private Double roundToHundredths(Double number)
    {
        double scale = Math.pow(10, 2);
        return Math.round(number * scale) / scale;
    }
}
