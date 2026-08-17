package repopulse.server.service;

import org.springframework.stereotype.Service;
import repopulse.server.entity.RepositoryEntity;
import repopulse.server.github.graphql.GithubGraphqlClient;
import repopulse.server.github.graphql.dto.pullrequest.GithubPullRequestConnection;

import java.time.Instant;

@Service
public class PullRequestSyncService
{
    private final GithubGraphqlClient githubClient;
    private final PullRequestPersistenceService pullRequestPersistenceService;

    public PullRequestSyncService(GithubGraphqlClient githubClient,
                                  PullRequestPersistenceService pullRequestPersistenceService)
    {
        this.githubClient = githubClient;
        this.pullRequestPersistenceService = pullRequestPersistenceService;
    }

    public void syncPullRequests(RepositoryEntity repository)
    {
        String cursor = null;
        boolean hasNextPage = true;

        while (hasNextPage)
        {
            GithubPullRequestConnection page = githubClient.getPullRequestsPage(
                    repository.getOwner(),
                    repository.getName(),
                    cursor
            );

            pullRequestPersistenceService.upsertPage(repository, page.nodes());
            hasNextPage = page.pageInfo().hasNextPage();
            cursor = page.pageInfo().endCursor();
        }

        repository.setLastSyncedAt(Instant.now());
    }
}
