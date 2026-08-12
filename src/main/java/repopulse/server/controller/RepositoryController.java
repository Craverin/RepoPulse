package repopulse.server.controller;

import org.springframework.web.bind.annotation.*;
import repopulse.server.dto.AnalyzeRepositoryRequest;
import repopulse.server.dto.analytics.RepositoryAnalyticsResponse;
import repopulse.server.service.RepositoryAnalyticsService;

@RestController
@RequestMapping("/api/repositories")
public class RepositoryController
{
    private final RepositoryAnalyticsService repositoryAnalyticsService;

    public RepositoryController(RepositoryAnalyticsService repositoryAnalyticsService)
    {
        this.repositoryAnalyticsService = repositoryAnalyticsService;
    }

    @GetMapping("/analyze")
    public RepositoryAnalyticsResponse getRepositoryAnalytics(@RequestBody AnalyzeRepositoryRequest request)
    {
        return repositoryAnalyticsService.analyzeRepository(request.repositoryUrl());
    }
}
