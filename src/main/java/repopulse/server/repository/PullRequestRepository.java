package repopulse.server.repository;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import repopulse.server.entity.PullRequestEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface PullRequestRepository extends JpaRepository<PullRequestEntity, Long> {
    Optional<PullRequestEntity> findByGithubId(Long githubId);
    List<PullRequestEntity> findAllByRepositoryId(Long repositoryId);
    List<PullRequestEntity> findAllByGithubIdIn(List<Long> githubIds);
}
