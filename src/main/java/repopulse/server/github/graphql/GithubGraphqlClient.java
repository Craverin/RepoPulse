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
import repopulse.server.github.graphql.dto.pullrequest.PullRequestPageVariables;
import repopulse.server.github.graphql.dto.pullrequest.PullRequestState;
import repopulse.server.github.graphql.dto.pullrequest.size.PullRequestSizeConnection;
import repopulse.server.github.graphql.dto.pullrequest.size.PullRequestSizePageData;
import repopulse.server.github.graphql.dto.pullrequest.summary.*;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Component
public class GithubGraphqlClient
{
    private static final String PULL_REQUESTS_SUMMARY_QUERY = """
            query GetPullRequestPage(
                $owner: String!,
                $name: String!,
                $cursor: String,
                $states: [PullRequestState!]!
                $pageSize: Int!
            )
            {
                repository(owner: $owner, name: $name)
                {
                    pullRequests(
                        first: $pageSize
                        after: $cursor
                        states: $states
                        orderBy:
                        {
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
            
                            author
                            {
                                login
                            }
            
                            isDraft
                            createdAt
                            updatedAt
                            closedAt
                            mergedAt
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

    private static final String PULL_REQUESTS_SIZE_QUERY = """
            query GetPullRequestSizePage(
                $owner: String!,
                $name: String!,
                $cursor: String,
                $states: [PullRequestState!]!
                $pageSize: Int!
            )
            {
                repository(owner: $owner, name: $name)
                {
                    pullRequests(
                        first: $pageSize
                        after: $cursor
                        states: $states
                        orderBy:
                        {
                            field: UPDATED_AT,
                            direction: DESC
                        }
                    )
                    {
                        nodes
                        {
                            fullDatabaseId
                            updatedAt
            
                            additions
                            deletions
                            changedFiles
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

    public PullRequestSummaryConnection getPullRequestSummaryPage(String owner,
                                                                  String repositoryName,
                                                                  String cursor,
                                                                  List<PullRequestState> states,
                                                                  int pageSize)
    {
        GithubGraphqlRequest<PullRequestPageVariables> request = new GithubGraphqlRequest<>(
                PULL_REQUESTS_SUMMARY_QUERY,
                new PullRequestPageVariables(owner, repositoryName, cursor, states, pageSize)
        );


        PullRequestSummaryPageData data = executeGraphql(
                request,
                new ParameterizedTypeReference<>() { }
        );


        return data.repository().pullRequests();
    }

    public PullRequestSizeConnection getPullRequestSizePage(String owner,
                                                            String repositoryName,
                                                            String cursor,
                                                            List<PullRequestState> states,
                                                            int pageSize)
    {
        GithubGraphqlRequest<PullRequestPageVariables> request = new GithubGraphqlRequest<>(
                PULL_REQUESTS_SIZE_QUERY,
                new PullRequestPageVariables(owner, repositoryName, cursor, states, pageSize)
        );

        PullRequestSizePageData data = executeGraphql(
                request,
                new ParameterizedTypeReference<>() { }
        );

        return data.repository().pullRequests();
    }

    private <T, V> V executeGraphql(GithubGraphqlRequest<T> request,
                                    ParameterizedTypeReference<GithubGraphqlResponse<V>> responseType)
    {
        long startedAt = System.nanoTime();

        GithubGraphqlResponse<V> response;

        try
        {
            response = restClient
                    .post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(responseType);
        }
        finally
        {
            double elapsedSeconds =
                    (System.nanoTime() - startedAt) / 1_000_000_000.0;

            System.out.printf(
                    Locale.ROOT,
                    "GitHub GraphQL request finished in %.3f seconds%n",
                    elapsedSeconds
            );
        }

        if (response == null)
            throw new IllegalStateException("GitHub GraphQL returned an empty response");

        checkForErrors(response.errors());

        return response.data();
    }

    private void checkForErrors(List<GithubGraphqlError> errors)
    {
        if (errors != null && !errors.isEmpty())
        {
            throw new IllegalStateException(errors.stream()
                    .map(GithubGraphqlError::message)
                    .collect(Collectors.joining("; ")));
        }
    }
}
