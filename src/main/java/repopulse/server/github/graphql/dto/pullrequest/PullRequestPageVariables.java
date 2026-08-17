package repopulse.server.github.graphql.dto.pullrequest;

public record PullRequestPageVariables(String owner, String name, String cursor) { }
