package repopulse.server.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import repopulse.server.dto.BaseRepositoryInfo;
import repopulse.server.dto.analytics.pullrequest.PullRequestAnalyticsResponse;
import repopulse.server.entity.RepositoryEntity;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

@Service
public class RepositoryAnalysisService
{
    private final RepositorySyncService repositorySyncService;
    private final PullRequestAnalyticsService analyticsService;

    public RepositoryAnalysisService(RepositorySyncService repositorySyncService,
                                     PullRequestAnalyticsService analyticsService)
    {
        this.repositorySyncService = repositorySyncService;
        this.analyticsService = analyticsService;
    }


    public PullRequestAnalyticsResponse analyze(String repositoryUrl)
    {
        return analyze(repositoryUrl, false);
    }

    public PullRequestAnalyticsResponse forceSyncAndAnalyze(String repositoryUrl)
    {
        return analyze(repositoryUrl, true);
    }

    private PullRequestAnalyticsResponse analyze(String repositoryUrl, boolean forceSync)
    {
        BaseRepositoryInfo repositoryInfo = parseRepositoryUrl(repositoryUrl);
        String owner = repositoryInfo.owner();
        String repositoryName = repositoryInfo.name();

        RepositoryEntity repository = repositorySyncService.syncRepository(owner, repositoryName, forceSync);

        return analyticsService.getOverview(repository.getId());
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

        if (!"github.com".equalsIgnoreCase(uri.getHost()))
        {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Expected URL in format https://github.com/{owner}/{repository}"
            );
        }

        List<String> pathParts = Arrays.stream(uri.getPath().split("/"))
                .filter(p -> !p.isEmpty())
                .toList();

        if (pathParts.size() != 2)
        {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Expected URL in format https://github.com/{owner}/{repository}"
            );
        }

        String owner = pathParts.get(0);
        String repositoryName = pathParts.get(1);

        if (repositoryName.endsWith(".git"))
            repositoryName = repositoryName.substring(0, repositoryName.length() - 4);

        return new BaseRepositoryInfo(owner, repositoryName);
    }
}
