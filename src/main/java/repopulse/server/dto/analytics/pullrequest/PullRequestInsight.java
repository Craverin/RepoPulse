package repopulse.server.dto.analytics.pullrequest;

import repopulse.server.dto.analytics.InsightSeverity;
import repopulse.server.dto.analytics.InsightValueUnit;

public record PullRequestInsight(PullRequestInsightType type,
                                 InsightSeverity severity,
                                 String description,
                                 Double currentValue,
                                 Double previousValue,
                                 InsightValueUnit unit) { }
