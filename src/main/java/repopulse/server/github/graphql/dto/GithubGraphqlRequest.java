package repopulse.server.github.graphql.dto;

public record GithubGraphqlRequest<T>(String query, T variables) { }

