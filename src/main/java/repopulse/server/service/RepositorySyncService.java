package repopulse.server.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import repopulse.server.api.GithubApiClient;
import repopulse.server.dto.GithubPullRequestResponse;
import repopulse.server.dto.GithubRepositoryResponse;
import repopulse.server.entity.PullRequestEntity;
import repopulse.server.entity.RepositoryEntity;
import repopulse.server.repository.PullRequestRepository;
import repopulse.server.repository.RepositoryRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@Transactional
public class RepositorySyncService
{
    private final GithubApiClient githubClient;
    private final RepositoryRepository repositoryRepository;
    private final PullRequestRepository pullRequestRepository;

    public RepositorySyncService(GithubApiClient githubClient,
                                      RepositoryRepository repositoryRepository,
                                      PullRequestRepository pullRequestRepository)
    {
        this.githubClient = githubClient;
        this.repositoryRepository = repositoryRepository;
        this.pullRequestRepository = pullRequestRepository;
    }

    public RepositoryEntity createOrUpdateRepository(GithubRepositoryResponse response)
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

    public void syncPullRequests(RepositoryEntity repositoryEntity)
    {
        String owner = repositoryEntity.getOwner();
        String repositoryName = repositoryEntity.getName();

        List<GithubPullRequestResponse> pullRequests = githubClient.getAllPullRequests(owner, repositoryName);

        for (GithubPullRequestResponse pullRequest : pullRequests)
        {
            PullRequestEntity currentEntity = pullRequestRepository.findByGithubId(pullRequest.id())
                    .orElse(null);

            if (currentEntity == null)
            {
                PullRequestEntity pullRequestEntity = new PullRequestEntity(
                        pullRequest.id(),
                        repositoryEntity,
                        pullRequest.number(),
                        pullRequest.state(),
                        pullRequest.title(),
                        pullRequest.htmlUrl(),
                        pullRequest.user().login(),
                        pullRequest.draft(),
                        pullRequest.updatedAt(),
                        pullRequest.createdAt(),
                        pullRequest.closedAt(),
                        pullRequest.mergedAt()
                );

                pullRequestRepository.save(pullRequestEntity);
                continue;
            }

            currentEntity.setState(pullRequest.state());
            currentEntity.setTitle(pullRequest.title());
            currentEntity.setHtmlUrl(pullRequest.htmlUrl());
            currentEntity.setAuthorLogin(pullRequest.user().login());
            currentEntity.setDraft(pullRequest.draft());
            currentEntity.setUpdatedAt(pullRequest.updatedAt());
            currentEntity.setClosedAt(pullRequest.closedAt());
            currentEntity.setMergedAt(pullRequest.mergedAt());
        }
    }

    public RepositoryEntity sync(String owner, String repositoryName, boolean forceSync)
    {
        GithubRepositoryResponse repositoryResponse = githubClient.getRepository(owner, repositoryName);
        RepositoryEntity repository = createOrUpdateRepository(repositoryResponse);

        if (forceSync || requiresSync(repository))
        {
            syncPullRequests(repository);
            repository.setLastSyncedAt(Instant.now());
        }

        return repository;
    }


    public boolean requiresSync(RepositoryEntity repository)
    {
        return repository.getLastSyncedAt().isBefore(Instant.now().minus(Duration.ofMinutes(15)));
    }

}
