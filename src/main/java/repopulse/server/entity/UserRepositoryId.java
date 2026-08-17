package repopulse.server.entity;

import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class UserRepositoryId implements Serializable
{
    private Long userId;
    private Long repositoryId;

    public UserRepositoryId(Long userId, Long repositoryId)
    {
        this.userId = userId;
        this.repositoryId = repositoryId;
    }

    protected UserRepositoryId() { }

    @Override
    public boolean equals(Object object)
    {
        if (this == object)
            return true;

        if (!(object instanceof UserRepositoryId other))
            return false;

        return Objects.equals(this.userId, other.userId)
                && Objects.equals(this.repositoryId, other.repositoryId);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(userId, repositoryId);
    }
}
