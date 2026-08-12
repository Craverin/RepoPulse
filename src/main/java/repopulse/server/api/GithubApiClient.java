package repopulse.server.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import repopulse.server.dto.GithubRepositoryResponse;
import repopulse.server.dto.GithubPullRequestResponse;

import java.util.List;

@Component
public class GithubApiClient
{
    private final RestClient restClient;

    public GithubApiClient(RestClient.Builder builder,
                           @Value("${github.api.base-url}") String baseUrl,
                           @Value("${github.api.version}") String version,
                           @Value("${github.api.token}") String token)
    {
        this.restClient = builder.baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.ACCEPT,"application/vnd.github+json")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .defaultHeader("X-GitHub-Api-Version", version)
                .build();
    }

    public GithubRepositoryResponse getRepository(String owner, String repositoryName)
    {
        return restClient.get().uri("/repos/" + owner + "/" + repositoryName)
                .retrieve()
                .body(GithubRepositoryResponse.class);
    }

    public List<GithubPullRequestResponse> getPullRequests(String owner, String repositoryName, int page)
    {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/repos/{owner}/{repo}/pulls")
                        .queryParam("state", "all")
                        .queryParam("sort", "updated")
                        .queryParam("direction", "desc")
                        .queryParam("per_page", 100)
                        .queryParam("page", page)
                        .build(owner, repositoryName)
                )
                .retrieve()
                .body(new ParameterizedTypeReference<>() { });
    }
}
