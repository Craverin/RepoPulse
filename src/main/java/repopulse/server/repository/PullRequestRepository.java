package repopulse.server.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import repopulse.server.entity.PullRequestEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface PullRequestRepository extends JpaRepository<PullRequestEntity, Long>
{
    List<PullRequestEntity> findAllByRepositoryId(Long repositoryId);
    List<PullRequestEntity> findAllByGithubIdIn(List<Long> githubIds);

    @Query("""
        SELECT pr
        FROM PullRequestEntity pr
        WHERE pr.repository.id = :repositoryId
          AND pr.additions IS NOT NULL
          AND pr.deletions IS NOT NULL
          AND pr.changedFiles IS NOT NULL
          AND (
                pr.state = "OPEN"

                OR (
                    pr.mergedAt IS NOT NULL
                    AND pr.mergedAt >= :periodStart
                    AND pr.mergedAt < :periodEnd
                )

                OR (
                    pr.closedAt IS NOT NULL
                    AND pr.closedAt >= :periodStart
                    AND pr.closedAt < :periodEnd
                )
          )
        """)

    List<PullRequestEntity> findInPeriodWithSizeInfo(@Param("repositoryId") long repositoryId,
                                                     @Param("periodStart") Instant periodStart,
                                                     @Param("periodEnd") Instant periodEnd);
}
