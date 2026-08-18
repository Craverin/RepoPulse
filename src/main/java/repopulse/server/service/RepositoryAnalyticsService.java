package repopulse.server.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import repopulse.server.analytics.PullRequestAnalyticsCalculator;
import repopulse.server.analytics.PullRequestInsightGenerator;
import repopulse.server.analytics.PullRequestPeriodMetricsCalculator;
import repopulse.server.analytics.PullRequestTrendsCalculator;
import repopulse.server.dto.BaseRepositoryInfo;
import repopulse.server.dto.analytics.pullrequest.PullRequestAnalytics;
import repopulse.server.dto.analytics.pullrequest.PullRequestInsight;
import repopulse.server.dto.analytics.pullrequest.PullRequestMonthlyMetrics;
import repopulse.server.dto.analytics.pullrequest.PullRequestPeriodMetrics;
import repopulse.server.dto.analytics.repository.RepositoryAnalyticsResponse;
import repopulse.server.dto.analytics.repository.RepositoryInsightsResponse;
import repopulse.server.dto.analytics.repository.RepositoryTrendsResponse;
import repopulse.server.entity.PullRequestEntity;
import repopulse.server.entity.RepositoryEntity;
import repopulse.server.repository.PullRequestRepository;
import repopulse.server.repository.RepositoryRepository;

import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class RepositoryAnalyticsService
{
    private final RepositorySyncService repositorySyncService;
    private final PullRequestAnalyticsCalculator analyticsCalculator;
    private final PullRequestTrendsCalculator trendsCalculator;
    private final PullRequestPeriodMetricsCalculator periodMetricsCalculator;
    private final PullRequestInsightGenerator insightGenerator;
    private final RepositoryRepository repositoryRepository;
    private final PullRequestRepository pullRequestRepository;

    public RepositoryAnalyticsService(RepositorySyncService repositorySyncService,
                                      PullRequestAnalyticsCalculator analyticsCalculator,
                                      PullRequestTrendsCalculator trendsCalculator,
                                      PullRequestPeriodMetricsCalculator periodMetricsCalculator,
                                      PullRequestInsightGenerator insightGenerator,
                                      PullRequestRepository pullRequestRepository,
                                      RepositoryRepository repositoryRepository)
    {
        this.repositorySyncService = repositorySyncService;
        this.analyticsCalculator = analyticsCalculator;
        this.trendsCalculator = trendsCalculator;
        this.periodMetricsCalculator = periodMetricsCalculator;
        this.insightGenerator = insightGenerator;
        this.pullRequestRepository = pullRequestRepository;
        this.repositoryRepository = repositoryRepository;
    }

    public RepositoryAnalyticsResponse analyze(String repositoryUrl)
    {
        return analyze(repositoryUrl, false);
    }

    public RepositoryAnalyticsResponse forceSyncAndAnalyze(String repositoryUrl)
    {
        return analyze(repositoryUrl, true);
    }

    public RepositoryAnalyticsResponse getAnalytics(long repositoryId)
    {
        RepositoryEntity repository = repositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Repository does not exist"));

        List<PullRequestEntity> pullRequests = pullRequestRepository.findAllByRepositoryId(repositoryId);
        PullRequestAnalytics analytics = analyticsCalculator.calculate(pullRequests);

        return new RepositoryAnalyticsResponse(
                repository.getId(),
                repository.getOwner(),
                repository.getName(),
                repository.getHtmlUrl(),
                repository.getLastSyncedAt(),
                analytics
        );
    }

    public RepositoryTrendsResponse getTrends(long repositoryId, int months)
    {
        RepositoryEntity repository = repositoryRepository.findById(repositoryId).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Repository does not exist")
        );


        List<PullRequestEntity> pullRequests = pullRequestRepository.findAllByRepositoryId(repositoryId);
        List<PullRequestMonthlyMetrics> monthlyMetrics = trendsCalculator.calculate(pullRequests, months);

        return new RepositoryTrendsResponse(
                repositoryId,
                repository.getLastSyncedAt(),
                monthlyMetrics
        );
    }

    public RepositoryInsightsResponse getInsights(long repositoryId)
    {
        RepositoryEntity repository = repositoryRepository.findById(repositoryId).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Repository does not exist")
        );

        Instant now = Instant.now();
        Instant currentPeriod = now.minus(30,  ChronoUnit.DAYS);
        Instant previousPeriod = now.minus(60,  ChronoUnit.DAYS);
        List<PullRequestEntity> pullRequests = pullRequestRepository.findAllByRepositoryId(repositoryId);

        PullRequestPeriodMetrics currentPeriodMetrics = periodMetricsCalculator.calculate(
                pullRequests,
                currentPeriod,
                now
        );

        PullRequestPeriodMetrics previousPeriodMetrics = periodMetricsCalculator.calculate(
                pullRequests,
                previousPeriod,
                currentPeriod
        );

        List<PullRequestInsight> insights = insightGenerator.generate(currentPeriodMetrics, previousPeriodMetrics);

        return new RepositoryInsightsResponse(
                repositoryId,
                repository.getLastSyncedAt(),
                insights
        );
    }

    private RepositoryAnalyticsResponse analyze(String repositoryUrl, boolean forceSync)
    {
        BaseRepositoryInfo repositoryInfo = parseRepositoryUrl(repositoryUrl);
        String owner = repositoryInfo.owner();
        String repositoryName = repositoryInfo.name();

        RepositoryEntity repository = repositorySyncService.syncRepository(owner, repositoryName, forceSync);
        List<PullRequestEntity> pullRequests = pullRequestRepository.findAllByRepositoryId(repository.getId());

        PullRequestAnalytics analytics = analyticsCalculator.calculate(pullRequests);

        return new RepositoryAnalyticsResponse(
                repository.getId(),
                repository.getOwner(),
                repository.getName(),
                repository.getHtmlUrl(),
                repository.getLastSyncedAt(),
                analytics
        );
    }

    private BaseRepositoryInfo parseRepositoryUrl(String repositoryUrl)
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

}
