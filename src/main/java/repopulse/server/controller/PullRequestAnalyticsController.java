package repopulse.server.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import repopulse.server.dto.analytics.pullrequest.PullRequestAnalyticsResponse;
import repopulse.server.dto.analytics.pullrequest.PullRequestInsightsResponse;
import repopulse.server.dto.analytics.pullrequest.PullRequestTrendsResponse;
import repopulse.server.dto.analytics.pullrequest.size.PullRequestSizeAnalyticsResponse;
import repopulse.server.service.PullRequestAnalyticsService;


@RestController
@RequestMapping("/api/repositories/{repositoryId}/analytics/pull-requests")
public class PullRequestAnalyticsController
{
    private final PullRequestAnalyticsService analyticsService;

    public PullRequestAnalyticsController(PullRequestAnalyticsService analyticsService)
    {
        this.analyticsService = analyticsService;
    }

    @GetMapping
    public PullRequestAnalyticsResponse getRepositoryAnalytics(@PathVariable long repositoryId)
    {
        return analyticsService.getOverview(repositoryId);
    }

    @GetMapping("/trends")
    public PullRequestTrendsResponse getTrends(@PathVariable long repositoryId,
                                               @RequestParam(defaultValue = "12") int months)
    {
        if (months < 1)
        {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Number of months must be greater than 0"
            );
        }

        return analyticsService.getTrends(repositoryId, months);
    }

    @GetMapping("/insights")
    public PullRequestInsightsResponse getInsights(@PathVariable long repositoryId)
    {
        return analyticsService.getInsights(repositoryId);
    }

    @GetMapping("/sizes")
    public PullRequestSizeAnalyticsResponse getSizeAnalytics(@PathVariable long repositoryId)
    {
        return analyticsService.getSizeAnalytics(repositoryId);
    }
}