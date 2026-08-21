package repopulse.server.service;

import org.springframework.stereotype.Service;
import repopulse.server.github.rest.GithubRestClient;
import repopulse.server.dto.GithubRepositoryResponse;
import repopulse.server.entity.RepositoryEntity;
import repopulse.server.repository.RepositoryRepository;

import java.time.Duration;
import java.time.Instant;

@Service
public class RepositorySyncService
{
    private final GithubRestClient githubClient;
    private final RepositoryRepository repositoryRepository;
    private final PullRequestSyncService pullRequestSyncService;

    public RepositorySyncService(GithubRestClient githubClient,
                                 RepositoryRepository repositoryRepository,
                                 PullRequestSyncService pullRequestSyncService)
    {
        this.githubClient = githubClient;
        this.repositoryRepository = repositoryRepository;
        this.pullRequestSyncService = pullRequestSyncService;
    }

    public RepositoryEntity upsertRepository(GithubRepositoryResponse response)
    {
        RepositoryEntity currentRepositoryEntity = repositoryRepository.findByGithubId(response.id())
                .orElse(null);

        if (currentRepositoryEntity == null)
        {
            RepositoryEntity repositoryEntity = new RepositoryEntity(
                    response.id(),
                    response.owner().login(),
                    response.name(),
                    response.htmlUrl(),
                    response.defaultBranch()
            );

            repositoryRepository.save(repositoryEntity);
            return repositoryEntity;
        }

        currentRepositoryEntity.setOwner(response.owner().login());
        currentRepositoryEntity.setName(response.name());
        currentRepositoryEntity.setHtmlUrl(response.htmlUrl());
        currentRepositoryEntity.setDefaultBranch(response.defaultBranch());

        return currentRepositoryEntity;
    }

    public RepositoryEntity syncRepository(String owner, String repositoryName, boolean forceSync)
    {
        GithubRepositoryResponse repositoryResponse = githubClient.getRepository(owner, repositoryName);
        RepositoryEntity repository = upsertRepository(repositoryResponse);

        if (forceSync || requiresSync(repository))
        {
            pullRequestSyncService.syncPullRequestSummaries(repository);
            repository.setSummarySyncedAt(Instant.now());
            pullRequestSyncService.enrichPullRequestSizes(repository);
            repository.setSizeSyncedAt(Instant.now());
        }

        return repository;
    }


    public boolean requiresSync(RepositoryEntity repository)
    {
        Instant syncTimeThreshold = Instant.now().minus(Duration.ofMinutes(15));
        Instant summarySyncedAt = repository.getSummarySyncedAt();
        Instant sizeSyncedAt = repository.getSizeSyncedAt();

        return summarySyncedAt == null || sizeSyncedAt == null ||
               summarySyncedAt.isBefore(syncTimeThreshold) || sizeSyncedAt.isBefore(syncTimeThreshold);
    }

}
