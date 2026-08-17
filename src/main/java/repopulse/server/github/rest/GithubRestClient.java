package repopulse.server.github.rest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import repopulse.server.dto.GithubRepositoryResponse;
import repopulse.server.dto.GithubPullRequestResponse;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        return restClient.get().uri("/repos/" + owner + "/" + repositoryName)
                .retrieve()
                .body(GithubRepositoryResponse.class);
    }

}
