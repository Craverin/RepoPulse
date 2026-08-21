package repopulse.server.dto.analytics.pullrequest.size;

public record PullRequestSizeStatistics(int completedPullRequests,

                                        Double medianChangedLines,
                                        Double medianChangedFiles,

                                        Integer p90ChangedLines,
                                        Integer p90ChangedFiles) { }
