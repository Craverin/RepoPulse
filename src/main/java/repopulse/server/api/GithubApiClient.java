package repopulse.server.api;

import org.apache.coyote.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
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
public class GithubApiClient
{
    private final RestClient restClient;
    private static final Pattern LINK_PATTERN = Pattern.compile("<([^>]+)>;\\srel=\"next\"");

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

    private ResponseEntity<GithubPullRequestResponse[]> getFirstPullRequestPage(String owner, String repositoryName)
    {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/repos/{owner}/{repo}/pulls")
                        .queryParam("state", "all")
                        .queryParam("sort", "updated")
                        .queryParam("direction", "desc")
                        .queryParam("per_page", 100)
                        .queryParam("page", 1)
                        .build(owner, repositoryName)
                )
                .retrieve()
                .toEntity(GithubPullRequestResponse[].class);
    }

    public List<GithubPullRequestResponse> getAllPullRequests(String owner, String repositoryName)
    {
        List<GithubPullRequestResponse> pullRequests = new ArrayList<>();
        ResponseEntity<GithubPullRequestResponse[]> resp = getFirstPullRequestPage(owner, repositoryName);

        while (true)
        {
            GithubPullRequestResponse[] body = resp.getBody();
            if (body != null)
            {
                System.out.println("BODY LENGTH: " + body.length);
                Collections.addAll(pullRequests, body);
            }

            URI nextPage = findNextPage(resp.getHeaders());
            if (nextPage == null)
                break;

            System.out.println("NEXT PAGE: " + nextPage);
            resp = restClient.get().uri(nextPage).retrieve().toEntity(GithubPullRequestResponse[].class);
        }

        return pullRequests;
    }

    private URI findNextPage(HttpHeaders headers)
    {
        String linkHeader = headers.getFirst(HttpHeaders.LINK);
        if (linkHeader == null)
            return null;

        Matcher matcher = LINK_PATTERN.matcher(linkHeader);
        if (matcher.find())
            return URI.create(matcher.group(1));

        return null;
    }


}
