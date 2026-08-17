package repopulse.server.github.graphql;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import repopulse.server.github.graphql.dto.GithubGraphqlError;
import repopulse.server.github.graphql.dto.GithubGraphqlRequest;
import repopulse.server.github.graphql.dto.GithubGraphqlResponse;
import repopulse.server.github.graphql.dto.pullrequest.GithubPullRequestConnection;
import repopulse.server.github.graphql.dto.pullrequest.PullRequestPageData;
import repopulse.server.github.graphql.dto.pullrequest.PullRequestPageVariables;
import repopulse.server.repository.PullRequestRepository;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class GithubGraphqlClient
{
    private static final String PULL_REQUESTS_QUERY = """
            query GetRepositoryPullRequestsPage(
                $owner: String!,
                $name: String!,
                $cursor: String
            )
            {
                repository(owner: $owner, name: $name)
                {
                    pullRequests(
                        first: 100,
                        after: $cursor,
                        states: [OPEN, CLOSED, MERGED],
                        orderBy: {
                            field: UPDATED_AT,
                            direction: DESC
                        }
                    )
                    {
                        totalCount
            
                        nodes
                        {
                            fullDatabaseId
                            number
                            state
                            title
                            url
            
                            author {
                                login
                            }
            
                            isDraft
                            createdAt
                            updatedAt
                            closedAt
                            mergedAt
            
                            additions
                            deletions
                            changedFiles
            
                            commits(first: 1)
                            {
                                totalCount
                            }
                        }
            
                        pageInfo
                        {
                            hasNextPage
                            endCursor
                        }
                    }
                }
            }
            """;

    private final RestClient restClient;

    public GithubGraphqlClient(RestClient.Builder builder,
                               @Value("${github.api.token}") String token,
                               @Value("${github.api.base-url}") String baseUrl)
    {
        this.restClient = builder
                .baseUrl(baseUrl + "/graphql")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
    }

    public GithubPullRequestConnection getPullRequestsPage(String owner,
                                                           String repositoryName,
                                                           String cursor)
    {
        GithubGraphqlRequest<PullRequestPageVariables> request = new GithubGraphqlRequest<>(
                PULL_REQUESTS_QUERY,
                new PullRequestPageVariables(owner, repositoryName, cursor)
        );

        GithubGraphqlResponse<PullRequestPageData> response = restClient
                .post()
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});


        if (response == null)
            throw new IllegalStateException("GitHub GraphQL returned an empty response");

        if (response.errors() != null && !response.errors().isEmpty())
        {
            throw new IllegalStateException(response.errors().stream()
                    .map(GithubGraphqlError::message)
                    .collect(Collectors.joining("; ")));
        }

        return response.data().repository().pullRequests();
    }

}
