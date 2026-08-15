package repopulse.server.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import org.springframework.web.server.ResponseStatusException;
import repopulse.server.dto.AnalyzeRepositoryRequest;
import repopulse.server.dto.analytics.repository.RepositoryAnalyticsResponse;
import repopulse.server.dto.analytics.repository.RepositoryInsightsResponse;
import repopulse.server.dto.analytics.repository.RepositoryTrendsResponse;
import repopulse.server.service.RepositoryAnalyticsService;

@RestController
@RequestMapping("/api/repositories")
public class RepositoryAnalyticsController
{
    private final RepositoryAnalyticsService repositoryAnalyticsService;

    public RepositoryAnalyticsController(RepositoryAnalyticsService repositoryAnalyticsService)
    {
        this.repositoryAnalyticsService = repositoryAnalyticsService;
    }

    @PostMapping("/analyze")
    public RepositoryAnalyticsResponse analyzeRepository(@RequestBody AnalyzeRepositoryRequest request)
    {
        return repositoryAnalyticsService.analyze(request.repositoryUrl());
    }

    @PostMapping("/sync")
    public RepositoryAnalyticsResponse syncRepository(@RequestBody AnalyzeRepositoryRequest request)
    {
       return repositoryAnalyticsService.forceSyncAndAnalyze(request.repositoryUrl());
    }

    @GetMapping("/{repositoryId}/analytics")
    public RepositoryAnalyticsResponse getRepositoryAnalytics(@PathVariable Long repositoryId)
    {
        if (repositoryId == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Repository ID is null");

        return repositoryAnalyticsService.getAnalytics(repositoryId);
    }

    @GetMapping("/{repositoryId}/trends")
    public RepositoryTrendsResponse getPullRequestTrends(@PathVariable Long repositoryId,
                                                         @RequestParam(defaultValue = "12") int months)
    {
        if (repositoryId == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Repository ID is null");
        if (months < 1)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Number of months must be greater than 0");

        return repositoryAnalyticsService.getTrends(repositoryId, months);
    }

    @GetMapping("/{repositoryId}/insights")
    public RepositoryInsightsResponse getPullRequestInsights(@PathVariable Long repositoryId)
    {
        if (repositoryId == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Repository ID is null");

        return repositoryAnalyticsService.getInsights(repositoryId);
    }
}
