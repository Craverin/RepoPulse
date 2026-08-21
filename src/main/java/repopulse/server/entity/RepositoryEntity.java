package repopulse.server.entity;

import jakarta.persistence.*;
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

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "summary_synced_at")
    private Instant summarySyncedAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "size_synced_at")
    private Instant sizeSyncedAt;

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

    public Instant getSummarySyncedAt() {
        return summarySyncedAt;
    }

    public void setSummarySyncedAt(Instant summarySyncedAt) {
        this.summarySyncedAt = summarySyncedAt;
    }

    public Instant getSizeSyncedAt() {
        return sizeSyncedAt;
    }

    public void setSizeSyncedAt(Instant sizeSyncedAt) {
        this.sizeSyncedAt = sizeSyncedAt;
    }
}
