package repopulse.server.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(
        name = "pull_requests",
        uniqueConstraints =
        {
            @UniqueConstraint
            (
                name = "UniqueRepositoryIdAndNumber",
                columnNames = {"repository_id", "number"}
            )
        }
)
public class PullRequestEntity
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pull_request_id")
    private Long id;

    @Column(name = "github_id", nullable = false, unique = true)
    private Long githubId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "repository_id", nullable = false)
    private RepositoryEntity repository;

    @Column(nullable = false)
    private Integer number;

    @Column(nullable = false)
    private String state;

    @Column(nullable = false)
    private String title;

    @Column(name = "html_url", nullable = false)
    private String htmlUrl;

    @Column(name = "author_login")
    private String authorLogin;

    private Integer additions;

    private Integer deletions;

    @Column(name = "changed_files")
    private Integer changedFiles;

    @Column(nullable = false)
    private boolean draft;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "closed_at")
    private Instant closedAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "merged_at")
    private Instant mergedAt;

    protected PullRequestEntity() { }

    public PullRequestEntity(Long githubId,
                             RepositoryEntity repository,
                             Integer number,
                             String state,
                             String title,
                             String htmlUrl,
                             String authorLogin,
                             boolean draft,
                             Instant updatedAt,
                             Instant createdAt,
                             Instant closedAt,
                             Instant mergedAt)
    {
        this.githubId = githubId;
        this.repository = repository;
        this.number = number;
        this.state = state;
        this.title = title;
        this.htmlUrl = htmlUrl;
        this.authorLogin = authorLogin;
        this.draft = draft;
        this.updatedAt = updatedAt;
        this.createdAt = createdAt;
        this.closedAt = closedAt;
        this.mergedAt = mergedAt;
    }

    public void setRepository(RepositoryEntity repository) {
        this.repository = repository;
    }

    public void setState(String state) {
        this.state = state;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setHtmlUrl(String htmlUrl) {
        this.htmlUrl = htmlUrl;
    }

    public void setAuthorLogin(String authorLogin) {
        this.authorLogin = authorLogin;
    }

    public void setDraft(boolean draft) {
        this.draft = draft;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public void setClosedAt(Instant closedAt) {
        this.closedAt = closedAt;
    }

    public void setMergedAt(Instant mergedAt) {
        this.mergedAt = mergedAt;
    }

    public Integer getNumber() {
        return number;
    }

    public String getTitle() {
        return title;
    }

    public String getHtmlUrl() {
        return htmlUrl;
    }

    public String getAuthorLogin() {
        return authorLogin;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public boolean isDraft() {
        return draft;
    }

    public Instant getMergedAt() {
        return mergedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getState() {
        return state;
    }

    public Long getGithubId() {
        return githubId;
    }

    public Integer getAdditions() {
        return additions;
    }

    public void setAdditions(Integer additions) {
        this.additions = additions;
    }

    public Integer getDeletions() {
        return deletions;
    }

    public void setDeletions(Integer deletions) {
        this.deletions = deletions;
    }

    public Integer getChangedLines()
    {
        if (additions == null || deletions == null)
            return null;

        return additions + deletions;
    }

    public Integer getChangedFiles() {
        return changedFiles;
    }

    public void setChangedFiles(Integer changedFiles) {
        this.changedFiles = changedFiles;
    }
}
