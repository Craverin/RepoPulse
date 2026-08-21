package repopulse.server.dto.analytics.pullrequest.size;

public record OversizedOpenPullRequest(int number,
                                       String title,
                                       String htmlUrl,
                                       String authorLogin,
                                       boolean draft,

                                       int changedLines,
                                       int changedFiles,
                                       int ageDays,
                                       int inactiveDays) { }
