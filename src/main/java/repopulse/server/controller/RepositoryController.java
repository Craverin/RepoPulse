package repopulse.server.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
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
}
