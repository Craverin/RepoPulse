package repopulse.server.github.graphql.dto.pullrequest.summary;

import repopulse.server.github.graphql.dto.GithubPageInfo;

import java.util.List;

public record PullRequestSummaryConnection(int totalCount,
                                           List<PullRequestSummaryNode> nodes,
                                           GithubPageInfo pageInfo) { }