package repopulse.server.service;

import org.springframework.stereotype.Service;
import repopulse.server.entity.RepositoryEntity;
import repopulse.server.github.graphql.GithubGraphqlClient;
import repopulse.server.github.graphql.dto.pullrequest.PullRequestState;
import repopulse.server.github.graphql.dto.pullrequest.size.PullRequestSizeConnection;
import repopulse.server.github.graphql.dto.pullrequest.size.PullRequestSizeNode;
import repopulse.server.github.graphql.dto.pullrequest.summary.PullRequestSummaryConnection;
import repopulse.server.github.graphql.dto.pullrequest.summary.PullRequestSummaryNode;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

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

    public void syncPullRequestSummaries(RepositoryEntity repository)
    {
        syncOpenPullRequestSummaries(repository);
        syncCompletedPullRequestSummaries(repository);
    }

    public void enrichPullRequestSizes(RepositoryEntity repository)
    {
        enrichOpenPullRequestSizes(repository);
        enrichCompletedPullRequestSizes(repository);
    }

    private void syncOpenPullRequestSummaries(RepositoryEntity repository)
    {
        String cursor = null;
        boolean hasNextPage = true;

        while (hasNextPage)
        {
            System.out.println("[SUMMARY-OPEN]: SENDING");
            PullRequestSummaryConnection page = githubClient.getPullRequestSummaryPage(
                    repository.getOwner(),
                    repository.getName(),
                    cursor,
                    List.of(PullRequestState.OPEN)
            );

            pullRequestPersistenceService.upsertSummaryPage(repository, page.nodes());
            hasNextPage = page.pageInfo().hasNextPage();
            cursor = page.pageInfo().endCursor();
        }
    }

    private void syncCompletedPullRequestSummaries(RepositoryEntity repository)
    {
        String cursor = null;
        boolean hasNextPage = true;
        int i = 0;

        Instant oneYearAgo = Instant.now().minus(365, ChronoUnit.DAYS);
        Instant threshold;

        if (repository.getLastSyncedAt().isAfter(oneYearAgo))
            threshold = Instant.now();
        else
            threshold = oneYearAgo;

        while (hasNextPage)
        {
            System.out.println("[SUMMARY-COMPLETED]: SENDING [" + (i * 100 + 1) + "-" + (i + 1) * 100 + "]");
            PullRequestSummaryConnection page = githubClient.getPullRequestSummaryPage(
                    repository.getOwner(),
                    repository.getName(),
                    cursor,
                    List.of(PullRequestState.CLOSED, PullRequestState.MERGED)
            );

            List<PullRequestSummaryNode> nodes = page.nodes();

            if (!nodes.getLast().updatedAt().isAfter(threshold))
            {
                System.out.println("FILTERING OUT PR (SIZE = " + nodes.size() + ")");
                nodes = nodes.stream()
                        .filter(pr -> !pr.updatedAt().isBefore(threshold))
                        .toList();

                System.out.println("FILTERED PR (SIZE = " + nodes.size() + ")");
                if (nodes.isEmpty())
                    return;
            }

            pullRequestPersistenceService.upsertSummaryPage(repository, page.nodes());
            hasNextPage = page.pageInfo().hasNextPage();
            cursor = page.pageInfo().endCursor();
            i++;
        }
    }

    private void enrichOpenPullRequestSizes(RepositoryEntity repository)
    {
        String cursor = null;
        boolean hasNextPage = true;
        int i = 0;

        while (hasNextPage)
        {
            System.out.println("[SIZE-OPEN]: SENDING [" + (i * 100 + 1) + "-" + (i + 1) * 100 + "]");
            PullRequestSizeConnection page = githubClient.getPullRequestSizePage(
                    repository.getOwner(),
                    repository.getName(),
                    cursor,
                    List.of(PullRequestState.OPEN)
            );

            pullRequestPersistenceService.upsertSizePage(page.nodes());
            hasNextPage = page.pageInfo().hasNextPage();
            cursor = page.pageInfo().endCursor();
            i++;
        }
    }

    private void enrichCompletedPullRequestSizes(RepositoryEntity repository)
    {
        String cursor = null;
        boolean hasNextPage = true;
        int i = 0;

        Instant oneYearAgo = Instant.now().minus(365, ChronoUnit.DAYS);
        Instant threshold;

        if (repository.getLastSyncedAt().isAfter(oneYearAgo))
            threshold = Instant.now();
        else
            threshold = oneYearAgo;

        while (hasNextPage)
        {
            System.out.println("[SIZE-COMPLETED]: SENDING [" + (i * 100 + 1) + "-" + (i + 1) * 100 + "]");
            PullRequestSizeConnection page = githubClient.getPullRequestSizePage(
                    repository.getOwner(),
                    repository.getName(),
                    cursor,
                    List.of(PullRequestState.CLOSED, PullRequestState.MERGED)
            );

            List<PullRequestSizeNode> nodes = page.nodes();
            if (!nodes.getLast().updatedAt().isAfter(threshold))
            {
                System.out.println("FILTERING OUT PR (SIZE = " + nodes.size() + ")");
                nodes = nodes.stream()
                        .filter(pr -> !pr.updatedAt().isBefore(threshold))
                        .toList();

                System.out.println("FILTERED PR (SIZE = " + nodes.size() + ")");
                if (nodes.isEmpty())
                    return;
            }

            pullRequestPersistenceService.upsertSizePage(page.nodes());
            hasNextPage = page.pageInfo().hasNextPage();
            cursor = page.pageInfo().endCursor();
            i++;
        }
    }
}
