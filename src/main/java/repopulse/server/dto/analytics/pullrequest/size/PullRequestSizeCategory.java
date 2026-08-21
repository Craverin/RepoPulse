package repopulse.server.dto.analytics.pullrequest.size;

public enum PullRequestSizeCategory
{
    SMALL,
    MEDIUM,
    LARGE,
    ENORMOUS;

    public static PullRequestSizeCategory getSizeCategory(int changedLines)
    {
        if (changedLines >= 1000)
            return ENORMOUS;
        if (changedLines >= 500)
            return LARGE;
        if (changedLines >= 100)
            return MEDIUM;

        return SMALL;
    }
}
