package repopulse.server.controller;

import org.springframework.web.bind.annotation.*;

import repopulse.server.dto.AnalyzeRepositoryRequest;
import repopulse.server.dto.analytics.pullrequest.PullRequestAnalyticsResponse;
import repopulse.server.service.RepositoryAnalysisService;

@RestController
@RequestMapping("/api/repositories")
public class RepositoryAnalyticsController
{
    private final RepositoryAnalysisService repositoryAnalysisService;

    public RepositoryAnalyticsController(RepositoryAnalysisService repositoryAnalysisService)
    {
        this.repositoryAnalysisService = repositoryAnalysisService;
    }

    @PostMapping("/analyze")
    public PullRequestAnalyticsResponse analyze(@RequestBody AnalyzeRepositoryRequest request)
    {
        return repositoryAnalysisService.analyze(request.repositoryUrl());
    }

    @PostMapping("/sync")
    public PullRequestAnalyticsResponse syncRepository(@RequestBody AnalyzeRepositoryRequest request)
    {
       return repositoryAnalysisService.forceSyncAndAnalyze(request.repositoryUrl());
    }
}
