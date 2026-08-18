package repopulse.server.github.graphql.dto.pullrequest.size;

import repopulse.server.github.graphql.dto.GithubPageInfo;

import java.util.List;

public record PullRequestSizeConnection(List<PullRequestSizeNode> nodes, GithubPageInfo pageInfo) { }