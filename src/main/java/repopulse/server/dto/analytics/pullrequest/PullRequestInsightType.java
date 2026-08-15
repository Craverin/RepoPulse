package repopulse.server.dto.analytics.pullrequest;

public enum PullRequestInsightType
{
    BACKLOG_GROWING,
    MEDIAN_MERGE_TIME_HIGH,
    MEDIAN_MERGE_TIME_INCREASED,
    MERGE_RATE_LOW,
    MERGE_RATE_DROPPED
}