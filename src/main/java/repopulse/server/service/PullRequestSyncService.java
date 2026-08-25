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
import java.time.ZoneOffset;
import java.util.List;

@Service
public class PullRequestSyncService
{
    private final int PULL_REQUEST_COUNT_PER_SUMMARY_PAGE = 100;
    private final int PULL_REQUEST_COUNT_PER_SIZE_PAGE = 75;

    private final GithubGraphqlClient githubClient;
    private final PullRequestPersistenceService pullRequestPersistenceService;

    public PullRequestSyncService(GithubGraphqlClient githubClient,
                                  PullRequestPersistenceService pullRequestPersistenceService)
    {
        this.githubClient = githubClient;
        this.pullRequestPersistenceService = pullRequestPersistenceService;
    }

    @Transactional
    public void syncPullRequestSizes(RepositoryEntity repository)
    {
        Instant syncStartedAt = Instant.now();
        Instant sizeSyncedAt = repository.getSizeSyncedAt();

        if (sizeSyncedAt == null)
        {
            syncPullRequestSizePages(
                    repository,
                    List.of(PullRequestState.OPEN),
                    null
            );

            syncPullRequestSizePages(
                    repository,
                    List.of(PullRequestState.CLOSED, PullRequestState.MERGED),
                    syncStartedAt.atZone(ZoneOffset.UTC).minusYears(1).toInstant()
            );
        }

        else
        {
            syncPullRequestSizePages(
                    repository,
                    List.of(PullRequestState.values()),
                    sizeSyncedAt.minusSeconds(300)
            );
        }

        repository.setSizeSyncedAt(syncStartedAt);
    }

    @Transactional
    public void syncPullRequestSummaries(RepositoryEntity repository)
    {
        String cursor = null;
        boolean hasNextPage = true;
        int i = 0;

        Instant threshold;

        if (repository.getSummarySyncedAt() != null)
            threshold = repository.getSummarySyncedAt().minusSeconds(300);
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
                    List.of(PullRequestState.values()),
                    PULL_REQUEST_COUNT_PER_SUMMARY_PAGE
            );

            List<PullRequestSummaryNode> nodes = page.nodes();

            if (nodes == null || nodes.isEmpty())
                return;

            if (threshold != null)
            {
                System.out.println("FILTERING OUT PR (SIZE = " + nodes.size() + ")");
                nodes = nodes.stream()
                        .takeWhile(pr -> !pr.updatedAt().isBefore(threshold))
                        .toList();
                System.out.println("FILTERED PR (SIZE = " + nodes.size() + ")");
            }

            pullRequestPersistenceService.upsertSummaryPage(repository, nodes);

            hasNextPage = page.pageInfo().hasNextPage();

            if (nodes.size() < page.nodes().size() || !hasNextPage)
                break;

            cursor = page.pageInfo().endCursor();
            i++;
        }

        repository.setSummarySyncedAt(syncStart);
    }

    private void syncPullRequestSizePages(RepositoryEntity repository,
                                          List<PullRequestState> states,
                                          Instant threshold)
    {
        String cursor = null;
        boolean hasNextPage = true;
        int i = 0;

        while (hasNextPage)
        {
            System.out.println("[SIZE-" + states.getFirst() + "]: SENDING [" + (i * 75 + 1) + "-" + (i + 1) * 75 + "]");
            PullRequestSizeConnection page = githubClient.getPullRequestSizePage(
                    repository.getOwner(),
                    repository.getName(),
                    cursor,
                    states,
                    PULL_REQUEST_COUNT_PER_SIZE_PAGE
            );

            List<PullRequestSizeNode> nodes = page.nodes();

            if (threshold != null)
            {
                System.out.println("FILTERING OUT PR (SIZE = " + nodes.size() + ")");
                nodes = nodes.stream()
                        .takeWhile(pr -> !pr.updatedAt().isBefore(threshold))
                        .toList();
                System.out.println("FILTERED PR (SIZE = " + nodes.size() + ")");
            }

            pullRequestPersistenceService.upsertSizePage(nodes);

            hasNextPage = page.pageInfo().hasNextPage();

            if (nodes.size() < page.nodes().size() || !hasNextPage)
                break;

            cursor = page.pageInfo().endCursor();
            i++;
        }
    }
}
