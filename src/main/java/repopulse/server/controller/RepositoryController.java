package repopulse.server.controller;

import org.springframework.web.bind.annotation.*;
import repopulse.server.dto.AnalyzeRepositoryRequest;
import repopulse.server.dto.analytics.RepositoryAnalyticsResponse;
import repopulse.server.service.RepositoryService;

@RestController
@RequestMapping("/api/repositories")
public class RepositoryController
{
    private final RepositoryService repositoryService;

    public RepositoryController(RepositoryService repositoryService)
    {
        this.repositoryService = repositoryService;
    }

    @GetMapping("/analyze")
    public RepositoryAnalyticsResponse getRepositoryAnalytics(@RequestBody AnalyzeRepositoryRequest request)
    {
        return repositoryService.getRepositoryAnalytics(request.repositoryUrl());
    }
}
