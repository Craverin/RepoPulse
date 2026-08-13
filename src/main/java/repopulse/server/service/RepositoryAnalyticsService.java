package repopulse.server.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import repopulse.server.analytics.PullRequestAnalyticsCalculator;
import repopulse.server.dto.BaseRepositoryInfo;
import repopulse.server.dto.analytics.PullRequestAnalytics;
import repopulse.server.dto.analytics.RepositoryAnalyticsResponse;
import repopulse.server.entity.PullRequestEntity;
import repopulse.server.entity.RepositoryEntity;
import repopulse.server.repository.PullRequestRepository;

import java.net.URI;
import java.util.*;

@Service
public class RepositoryAnalyticsService
{
    private final RepositorySyncService repositorySyncService;
    private final PullRequestAnalyticsCalculator pullRequestAnalyticsCalculator;
    private final PullRequestRepository pullRequestRepository;

    public RepositoryAnalyticsService(RepositorySyncService repositorySyncService,
                                      PullRequestRepository pullRequestRepository,
                                      PullRequestAnalyticsCalculator pullRequestAnalyticsCalculator)
    {
        this.repositorySyncService = repositorySyncService;
        this.pullRequestAnalyticsCalculator = pullRequestAnalyticsCalculator;
        this.pullRequestRepository = pullRequestRepository;
    }

    public RepositoryAnalyticsResponse analyze(String repositoryUrl)
    {
        return analyze(repositoryUrl, false);
    }

    public RepositoryAnalyticsResponse forceSyncAndAnalyze(String repositoryUrl)
    {
        return analyze(repositoryUrl, true);
    }

    private RepositoryAnalyticsResponse analyze(String repositoryUrl, boolean forceSync)
    {
        BaseRepositoryInfo repositoryInfo = parseRepositoryUrl(repositoryUrl);
        String owner = repositoryInfo.owner();
        String repositoryName = repositoryInfo.name();

        RepositoryEntity repository = repositorySyncService.sync(owner, repositoryName, forceSync);
        List<PullRequestEntity> pullRequests = pullRequestRepository.findAllByRepositoryId(repository.getId());

        PullRequestAnalytics analytics = pullRequestAnalyticsCalculator.calculate(pullRequests);

        return new RepositoryAnalyticsResponse(
                repository.getId(),
                repository.getOwner(),
                repository.getName(),
                repository.getHtmlUrl(),
                repository.getLastSyncedAt(),
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


}
