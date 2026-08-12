package repopulse.server.dto.analytics;

public record StalePullRequestResponse(int number,
                                       String title,
                                       String htmlUrl,
                                       String authorLogin,
                                       long inactiveDays) { }