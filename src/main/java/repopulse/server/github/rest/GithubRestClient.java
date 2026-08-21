package repopulse.server.github.rest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import repopulse.server.dto.GithubRepositoryResponse;

@Component
public class GithubRestClient
{
    private final RestClient restClient;

    public GithubRestClient(RestClient.Builder builder,
                            @Value("${github.api.base-url}") String baseUrl,
                            @Value("${github.api.version}") String version,
                            @Value("${github.api.token}") String token)
    {
        this.restClient = builder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.ACCEPT,"application/vnd.github+json")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .defaultHeader("X-GitHub-Api-Version", version)
                .build();
    }

    public GithubRepositoryResponse getRepository(String owner, String repositoryName)
    {
        return restClient.get()
                .uri("/repos/" + owner + "/" + repositoryName)
                .retrieve()
                .body(GithubRepositoryResponse.class);
    }

}
