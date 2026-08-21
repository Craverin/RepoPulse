package repopulse.server.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import repopulse.server.analytics.*;
import repopulse.server.dto.analytics.pullrequest.PullRequestAnalytics;
import repopulse.server.dto.analytics.pullrequest.PullRequestInsight;
import repopulse.server.dto.analytics.pullrequest.PullRequestMonthlyMetrics;
import repopulse.server.dto.analytics.pullrequest.PullRequestPeriodMetrics;
import repopulse.server.dto.analytics.pullrequest.PullRequestAnalyticsResponse;
import repopulse.server.dto.analytics.pullrequest.PullRequestInsightsResponse;
import repopulse.server.dto.analytics.pullrequest.PullRequestTrendsResponse;
import repopulse.server.dto.analytics.pullrequest.size.PullRequestSizeAnalytics;
import repopulse.server.dto.analytics.pullrequest.size.PullRequestSizeAnalyticsResponse;
import repopulse.server.entity.PullRequestEntity;
import repopulse.server.entity.RepositoryEntity;
import repopulse.server.repository.PullRequestRepository;
import repopulse.server.repository.RepositoryRepository;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class PullRequestAnalyticsService
{
    private final PullRequestAnalyticsCalculator analyticsCalculator;
    private final PullRequestTrendsCalculator trendsCalculator;
    private final PullRequestPeriodMetricsCalculator periodMetricsCalculator;
    private final PullRequestSizeAnalyticsCalculator sizeAnalyticsCalculator;
    private final PullRequestInsightGenerator insightGenerator;
    private final RepositoryRepository repositoryRepository;
    private final PullRequestRepository pullRequestRepository;

    public PullRequestAnalyticsService(PullRequestAnalyticsCalculator analyticsCalculator,
                                       PullRequestTrendsCalculator trendsCalculator,
                                       PullRequestPeriodMetricsCalculator periodMetricsCalculator,
                                       PullRequestSizeAnalyticsCalculator sizeAnalyticsCalculator,
                                       PullRequestInsightGenerator insightGenerator,
                                       PullRequestRepository pullRequestRepository,
                                       RepositoryRepository repositoryRepository)
    {
        this.analyticsCalculator = analyticsCalculator;
        this.trendsCalculator = trendsCalculator;
        this.periodMetricsCalculator = periodMetricsCalculator;
        this.sizeAnalyticsCalculator = sizeAnalyticsCalculator;
        this.insightGenerator = insightGenerator;
        this.pullRequestRepository = pullRequestRepository;
        this.repositoryRepository = repositoryRepository;
    }

    public PullRequestAnalyticsResponse getOverview(long repositoryId)
    {
        RepositoryEntity repository = getRepository(repositoryId);

        List<PullRequestEntity> pullRequests = pullRequestRepository.findAllByRepositoryId(repositoryId);
        PullRequestAnalytics analytics = analyticsCalculator.calculate(pullRequests);

        return new PullRequestAnalyticsResponse(
                repository.getId(),
                repository.getOwner(),
                repository.getName(),
                repository.getHtmlUrl(),
                repository.getSummarySyncedAt(),
                analytics
        );
    }

    public PullRequestTrendsResponse getTrends(long repositoryId, int months)
    {
        RepositoryEntity repository = getRepository(repositoryId);

        List<PullRequestEntity> pullRequests = pullRequestRepository.findAllByRepositoryId(repositoryId);
        List<PullRequestMonthlyMetrics> monthlyMetrics = trendsCalculator.calculate(pullRequests, months);

        return new PullRequestTrendsResponse(
                repositoryId,
                repository.getSummarySyncedAt(),
                monthlyMetrics
        );
    }

    public PullRequestInsightsResponse getInsights(long repositoryId)
    {
        RepositoryEntity repository = getRepository(repositoryId);

        Instant periodEnd = repository.getSummarySyncedAt();
        Instant currentPeriodStart = periodEnd.minus(30,  ChronoUnit.DAYS);
        Instant previousPeriodStart = periodEnd.minus(60,  ChronoUnit.DAYS);
        List<PullRequestEntity> pullRequests = pullRequestRepository.findAllByRepositoryId(repositoryId);

        PullRequestPeriodMetrics currentPeriodMetrics = periodMetricsCalculator.calculate(
                pullRequests,
                currentPeriodStart,
                periodEnd
        );

        PullRequestPeriodMetrics previousPeriodMetrics = periodMetricsCalculator.calculate(
                pullRequests,
                previousPeriodStart,
                currentPeriodStart
        );

        List<PullRequestInsight> insights = insightGenerator.generate(currentPeriodMetrics, previousPeriodMetrics);

        return new PullRequestInsightsResponse(
                repositoryId,
                repository.getSummarySyncedAt(),
                insights
        );
    }


    public PullRequestSizeAnalyticsResponse getSizeAnalytics(long repositoryId)
    {
        RepositoryEntity repository = getRepository(repositoryId);

        if (repository.getSizeSyncedAt() == null)
        {
            throw new ResponseStatusException(HttpStatus.NO_CONTENT,
                    "Unable to get size analytics for repository"
            );
        }
        Instant periodEnd = repository.getSizeSyncedAt();
        Instant periodStart = periodEnd.atZone(ZoneOffset.UTC).minusYears(1).toInstant();

        List<PullRequestEntity> pullRequests = pullRequestRepository.findInPeriodWithSizeInfo(
                repositoryId,
                periodStart,
                periodEnd
        );

        PullRequestSizeAnalytics sizeAnalytics = sizeAnalyticsCalculator.calculate(pullRequests);

        return new PullRequestSizeAnalyticsResponse(
                repositoryId,
                repository.getOwner(),
                repository.getName(),
                repository.getHtmlUrl(),
                periodStart,
                periodEnd,
                repository.getSizeSyncedAt(),
                sizeAnalytics
        );
    }

    private RepositoryEntity getRepository(long repositoryId)
    {
        return repositoryRepository.findById(repositoryId).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Repository does not exist")
        );
    }
}
