package repopulse.server.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "user_repositories")
public class UserRepositoryEntity
{
    @EmbeddedId
    private UserRepositoryId id;

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @MapsId("repositoryId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "repository_id", nullable = false)
    private RepositoryEntity repository;

    @Generated
    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(
            name = "tracked_at",
            nullable = false,
            insertable = false,
            updatable = false
    )
    private Instant trackedAt;

    protected UserRepositoryEntity() { }

    public UserRepositoryEntity(UserEntity user, RepositoryEntity repository)
    {
        this.user = user;
        this.repository = repository;
        this.id = new UserRepositoryId(user.getId(), repository.getId());
    }
}
