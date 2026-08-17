package repopulse.server.github.graphql.dto;

import java.util.List;

public record GithubGraphqlResponse<T>(T data, List<GithubGraphqlError> errors) { }