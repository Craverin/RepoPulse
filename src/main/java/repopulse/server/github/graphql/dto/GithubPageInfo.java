package repopulse.server.github.graphql.dto;

public record GithubPageInfo(boolean hasNextPage, String endCursor) { }
