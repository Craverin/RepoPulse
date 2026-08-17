package repopulse.server.service;

import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import repopulse.server.entity.PullRequestEntity;
import repopulse.server.entity.RepositoryEntity;
import repopulse.server.github.graphql.dto.pullrequest.GithubPullRequestNode;
import repopulse.server.repository.PullRequestRepository;
import repopulse.server.repository.RepositoryRepository;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
public class PullRequestPersistenceService
{
    private final PullRequestRepository pullRequestRepository;
    private final RepositoryRepository repositoryRepository;

    public PullRequestPersistenceService(PullRequestRepository pullRequestRepository,
                                         RepositoryRepository repositoryRepository)
    {
        this.pullRequestRepository = pullRequestRepository;
        this.repositoryRepository = repositoryRepository;
    }


    public void upsertPage(RepositoryEntity repositoryEntity, List<GithubPullRequestNode> pullRequests)
    {
        List<Long> githubIds = pullRequests.stream()
                .map(pr -> Long.parseLong(pr.fullDatabaseId()))
                .toList();

        Map<Long, PullRequestEntity> pullRequestEntityByGithubId =
                pullRequestRepository.findAllByGithubIdIn(githubIds)
                        .stream()
                        .collect(Collectors.toMap(PullRequestEntity::getGithubId, pr -> pr));

        for (GithubPullRequestNode pullRequest : pullRequests)
        {
            long githubId = Long.parseLong(pullRequest.fullDatabaseId());
            PullRequestEntity pullRequestEntity = pullRequestEntityByGithubId.get(githubId);

            if (pullRequestEntity == null)
                createAndSavePullRequest(pullRequest, repositoryEntity);
            else
                updatePullRequest(pullRequestEntity, pullRequest);
        }
    }

    public void createAndSavePullRequest(GithubPullRequestNode pullRequest, RepositoryEntity repositoryEntity)
    {
        pullRequestRepository.save(new PullRequestEntity(
                Long.parseLong(pullRequest.fullDatabaseId()),
                repositoryEntity,
                pullRequest.number(),
                pullRequest.state(),
                pullRequest.title(),
                pullRequest.url(),
                pullRequest.author().login(),
                pullRequest.additions(),
                pullRequest.deletions(),
                pullRequest.changedFiles(),
                pullRequest.commits().totalCount(),
                pullRequest.isDraft(),
                pullRequest.updatedAt(),
                pullRequest.createdAt(),
                pullRequest.closedAt(),
                pullRequest.mergedAt()
        ));
    }

    public void updatePullRequest(PullRequestEntity pullRequestEntity, GithubPullRequestNode pullRequest)
    {
        pullRequestEntity.setState(pullRequest.state());
        pullRequestEntity.setTitle(pullRequest.title());
        pullRequestEntity.setHtmlUrl(pullRequest.url());
        pullRequestEntity.setAuthorLogin(pullRequest.author() == null ? null : pullRequest.author().login());
        pullRequestEntity.setAdditions(pullRequest.additions());
        pullRequestEntity.setDeletions(pullRequest.deletions());
        pullRequestEntity.setChangedFiles(pullRequest.changedFiles());
        pullRequestEntity.setCommitsCount(pullRequest.commits().totalCount());
        pullRequestEntity.setDraft(pullRequest.isDraft());
        pullRequestEntity.setUpdatedAt(pullRequest.updatedAt());
        pullRequestEntity.setClosedAt(pullRequest.closedAt());
        pullRequestEntity.setMergedAt(pullRequest.mergedAt());
    }
}
