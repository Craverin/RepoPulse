package repopulse.server.dto.analytics;

public record StalePullRequest(int number,
                               String title,
                               String htmlUrl,
                               String authorLogin,
                               long inactiveDays) { }