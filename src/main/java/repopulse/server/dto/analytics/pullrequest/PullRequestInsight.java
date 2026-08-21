package repopulse.server.dto.analytics.pullrequest;

public record PullRequestInsight(PullRequestInsightType type,
                                 InsightSeverity severity,
                                 String description,
                                 Double currentValue,
                                 Double previousValue,
                                 InsightValueUnit unit) { }
