package repopulse.server.github.graphql.dto.pullrequest;

import repopulse.server.github.graphql.dto.GithubPageInfo;

import java.util.List;

public record GithubPullRequestConnection(int totalCount,
                                          List<GithubPullRequestNode> nodes,
                                          GithubPageInfo pageInfo) { }