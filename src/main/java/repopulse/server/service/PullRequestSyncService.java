package repopulse.server.service;

import jakarta.transaction.Transactional;
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

    @Transactional
    public void enrichPullRequestSizes(RepositoryEntity repository)
    {
        Instant syncStart = Instant.now();

        enrichOpenPullRequestSizes(repository);
        enrichCompletedPullRequestSizes(repository);

        repository.setSizeSyncedAt(syncStart);
    }

    @Transactional
    public void syncPullRequestSummaries(RepositoryEntity repository)
    {
        String cursor = null;
        boolean hasNextPage = true;
        int i = 0;

        Instant threshold;

        if (repository.getSummarySyncedAt() != null)
            threshold = repository.getSummarySyncedAt().minusSeconds(180);
        else
            threshold = null;

        Instant syncStart = Instant.now();

        while (hasNextPage)
        {
            System.out.println("[SUMMARY-COMPLETED]: SENDING [" + (i * 100 + 1) + "-" + (i + 1) * 100 + "]");
            PullRequestSummaryConnection page = githubClient.getPullRequestSummaryPage(
                    repository.getOwner(),
                    repository.getName(),
                    cursor,
                    List.of(PullRequestState.values())
            );

            List<PullRequestSummaryNode> nodes = page.nodes();

            if (threshold != null && nodes.getLast().updatedAt().isBefore(threshold))
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

        repository.setSummarySyncedAt(syncStart);
    }


    private void enrichOpenPullRequestSizes(RepositoryEntity repository)
    {
        String cursor = null;
        boolean hasNextPage = true;
        int i = 0;

        while (hasNextPage)
        {
            System.out.println("[SIZE-OPEN]: SENDING [" + (i * 75 + 1) + "-" + (i + 1) * 75 + "]");
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

        if (repository.getSizeSyncedAt() != null && repository.getSizeSyncedAt().isAfter(oneYearAgo))
            threshold = repository.getSizeSyncedAt().minusSeconds(180);
        else
            threshold = oneYearAgo;

        while (hasNextPage)
        {
            System.out.println("[SIZE-COMPLETED]: SENDING [" + (i * 75 + 1) + "-" + (i + 1) * 75 + "]");
            PullRequestSizeConnection page = githubClient.getPullRequestSizePage(
                    repository.getOwner(),
                    repository.getName(),
                    cursor,
                    List.of(PullRequestState.CLOSED, PullRequestState.MERGED)
            );

            List<PullRequestSizeNode> nodes = page.nodes();
            if (nodes.getLast().updatedAt().isBefore(threshold))
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
