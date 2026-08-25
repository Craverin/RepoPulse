package repopulse.server.github.graphql.dto.pullrequest;

import java.util.List;

public record PullRequestPageVariables(String owner,
                                       String name,
                                       String cursor,
                                       List<PullRequestState> states,
                                       int pageSize) { }

