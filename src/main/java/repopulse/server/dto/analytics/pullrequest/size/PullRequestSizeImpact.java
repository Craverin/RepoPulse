package repopulse.server.dto.analytics.pullrequest.size;

public record PullRequestSizeImpact(Double oversizedToNonOversizedMedianMergeTimeRatio,

                                    int correlationSampleSize,
                                    Double changedLinesToMergeTimeSpearmanCorrelation,
                                    Double changedFilesToMergeTimeSpearmanCorrelation) { }
