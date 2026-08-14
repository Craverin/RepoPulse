package repopulse.server.dto;

public record StalePullRequest(int number,
                               String title,
                               String htmlUrl,
                               String authorLogin,
                               long inactiveDays) { }