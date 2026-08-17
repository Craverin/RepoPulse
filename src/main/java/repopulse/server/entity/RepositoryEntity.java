package repopulse.server.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "repositories")
public class RepositoryEntity
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "repository_id")
    private Long id;

    @Column(name = "github_id", nullable = false, unique = true)
    private Long githubId;

    @Column(nullable = false)
    private String owner;

    @Column(nullable = false)
    private String name;

    @Column(name = "html_url", nullable = false)
    private String htmlUrl;

    @Column(name = "default_branch", nullable = false)
    private String defaultBranch;

    @Generated
    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "last_synced_at", nullable = false)
    private Instant lastSyncedAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "orphaned_at")
    private Instant orphanedAt;

    protected RepositoryEntity() { }

    public RepositoryEntity(
            Long githubId,
            String owner,
            String name,
            String htmlUrl,
            String defaultBranch)
    {
        this.githubId = githubId;
        this.owner = owner;
        this.name = name;
        this.htmlUrl = htmlUrl;
        this.defaultBranch = defaultBranch;
    }

    public Long getId() {
        return id;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setHtmlUrl(String htmlUrl) {
        this.htmlUrl = htmlUrl;
    }

    public void setDefaultBranch(String defaultBranch) {
        this.defaultBranch = defaultBranch;
    }

    public String getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    public String getHtmlUrl() {
        return htmlUrl;
    }

    public String getDefaultBranch() {
        return defaultBranch;
    }

    public void setLastSyncedAt(Instant lastSyncedAt) {
        this.lastSyncedAt = lastSyncedAt;
    }

    public Instant getLastSyncedAt() {
        return lastSyncedAt;
    }
}
