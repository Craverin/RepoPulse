package repopulse;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import repopulse.server.entity.PullRequestEntity;
import repopulse.server.entity.RepositoryEntity;
import repopulse.server.repository.PullRequestRepository;
import repopulse.server.repository.RepositoryRepository;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "github.api.token=test-token")
@AutoConfigureMockMvc
@Testcontainers
class RepositoryAnalysisIntegrationTest
{
    private static final String REPOSITORY_REQUEST = """
            {"repositoryUrl": "https://github.com/someuser/somerepo"}
            """;

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    // HTTP/2 causes error, thus is disabled
    static WireMockServer github = new WireMockServer(wireMockConfig().http2PlainDisabled(true));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private PullRequestRepository pullRequestRepository;

    private Instant now;

    @BeforeAll
    static void startGithub()
    {
        github.start();
    }

    @AfterAll
    static void stopGithub()
    {
        github.stop();
    }

    @DynamicPropertySource
    static void configureGithub(DynamicPropertyRegistry registry)
    {
        registry.add("github.api.base-url", github::baseUrl);
    }

    @BeforeEach
    void setUp()
    {
        pullRequestRepository.deleteAllInBatch();
        repositoryRepository.deleteAllInBatch();
        github.resetAll();

        /* Instant have a precision to 9-th digit (nanoseconds), while PostgreSQL only have 6 (microseconds).
        Because there's no need to check time to microseconds, it is truncated to seconds. */
        now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    }

    @Test
    void analyze_singleOpenPullRequest_savesRepositoryAndPullRequestAndReturnsAnalytics() throws Exception
    {
        stubInitialGithubResponses();

        mockMvc.perform(post("/api/repositories/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REPOSITORY_REQUEST)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.owner").value("someuser"))
                .andExpect(jsonPath("$.name").value("somerepo"))
                .andExpect(jsonPath("$.dataLastSyncedAt").isNotEmpty())
                .andExpect(jsonPath("$.pullRequestAnalytics.totalPullRequests").value(1))
                .andExpect(jsonPath("$.pullRequestAnalytics.openPullRequests").value(1))
                .andExpect(jsonPath("$.pullRequestAnalytics.mergedPullRequests").value(0));

        assertThat(repositoryRepository.count()).isEqualTo(1);

        RepositoryEntity repository = repositoryRepository.findByGithubId(100L).orElseThrow();

        assertThat(repository.getOwner()).isEqualTo("someuser");
        assertThat(repository.getName()).isEqualTo("somerepo");
        assertThat(repository.getHtmlUrl()).isEqualTo("https://github.com/someuser/somerepo");
        assertThat(repository.getDefaultBranch()).isEqualTo("main");
        assertThat(repository.getSummarySyncedAt()).isNotNull();
        assertThat(repository.getSizeSyncedAt()).isNotNull();

        List<PullRequestEntity> pullRequests = pullRequestRepository.findAllByRepositoryId(repository.getId());
        assertThat(pullRequests.size()).isEqualTo(1);

        PullRequestEntity pullRequest = pullRequests.getFirst();

        assertThat(pullRequest.getGithubId()).isEqualTo(200L);
        assertThat(pullRequest.getNumber()).isEqualTo(1);
        assertThat(pullRequest.getTitle()).isEqualTo("Add dashboard");
        assertThat(pullRequest.getState()).isEqualTo("OPEN");
        assertThat(pullRequest.getAuthorLogin()).isEqualTo("alice");
        assertThat(pullRequest.getCreatedAt()).isEqualTo(now.minus(2, ChronoUnit.DAYS));
        assertThat(pullRequest.getUpdatedAt()).isEqualTo(pullRequest.getCreatedAt());
        assertThat(pullRequest.getMergedAt()).isNull();
        assertThat(pullRequest.getAdditions()).isEqualTo(40);
        assertThat(pullRequest.getDeletions()).isEqualTo(10);
        assertThat(pullRequest.getChangedFiles()).isEqualTo(2);
    }

    @Test
    void analyze_pullRequestIsAddedAfterFirstSync_onSecondSyncReturnsAlreadySavedAnalytics() throws Exception
    {
        stubInitialGithubResponses();

        mockMvc.perform(post("/api/repositories/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REPOSITORY_REQUEST)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pullRequestAnalytics.totalPullRequests").value(1))
                .andExpect(jsonPath("$.pullRequestAnalytics.openPullRequests").value(1));

        assertThat(repositoryRepository.count()).isEqualTo(1);

        RepositoryEntity repository = repositoryRepository.findByGithubId(100L).orElseThrow();

        assertThat(repository.getSummarySyncedAt()).isNotNull();
        Instant summarySyncedAt = repository.getSummarySyncedAt();

        github.resetRequests();

        stubGraphql(
                "GetPullRequestPage",
                "[\"OPEN\", \"CLOSED\", \"MERGED\"]",
                "open-and-merged-pull-requests.json"
        );

        mockMvc.perform(post("/api/repositories/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REPOSITORY_REQUEST)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.owner").value("someuser"))
                .andExpect(jsonPath("$.name").value("somerepo"))
                .andExpect(jsonPath("$.pullRequestAnalytics.totalPullRequests").value(1))
                .andExpect(jsonPath("$.pullRequestAnalytics.openPullRequests").value(1))
                .andExpect(jsonPath("$.pullRequestAnalytics.mergedPullRequests").value(0));

        assertThat(repositoryRepository.count()).isEqualTo(1);
        assertThat(pullRequestRepository.findAllByRepositoryId(repository.getId()).size()).isEqualTo(1);

        repository = repositoryRepository.findByGithubId(100L).orElseThrow();
        assertThat(repository.getSummarySyncedAt()).isEqualTo(summarySyncedAt);

        github.verify(0, postRequestedFor(urlEqualTo("/graphql")));
    }

    @Test
    void forceSync_pullRequestIsAddedAfterFirstSync_onSecondsSyncReturnsFreshAnalytics() throws Exception
    {
        stubInitialGithubResponses();

        mockMvc.perform(post("/api/repositories/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REPOSITORY_REQUEST)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pullRequestAnalytics.totalPullRequests").value(1))
                .andExpect(jsonPath("$.pullRequestAnalytics.openPullRequests").value(1));

        assertThat(repositoryRepository.count()).isEqualTo(1);

        RepositoryEntity repository = repositoryRepository.findByGithubId(100L).orElseThrow();

        assertThat(repository.getSummarySyncedAt()).isNotNull();
        Instant summarySyncedAt = repository.getSummarySyncedAt();

        github.resetRequests();

        stubGraphql(
                "GetPullRequestPage",
                "[\"OPEN\", \"CLOSED\", \"MERGED\"]",
                "open-and-merged-pull-requests.json"
        );

        stubGraphql(
                "GetPullRequestSizePage",
                "[\"OPEN\", \"CLOSED\", \"MERGED\"]",
                "merged-pull-request-size.json"
        );

        mockMvc.perform(post("/api/repositories/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REPOSITORY_REQUEST)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.owner").value("someuser"))
                .andExpect(jsonPath("$.name").value("somerepo"))
                .andExpect(jsonPath("$.pullRequestAnalytics.totalPullRequests").value(2))
                .andExpect(jsonPath("$.pullRequestAnalytics.openPullRequests").value(1))
                .andExpect(jsonPath("$.pullRequestAnalytics.mergedPullRequests").value(1));

        assertThat(repositoryRepository.count()).isEqualTo(1);

        repository = repositoryRepository.findByGithubId(100L).orElseThrow();
        assertThat(repository.getSummarySyncedAt()).isNotEqualTo(summarySyncedAt);

        List<PullRequestEntity> pullRequests = pullRequestRepository.findAllByRepositoryId(repository.getId());
        assertThat(pullRequests.size()).isEqualTo(2);

        PullRequestEntity mergedPullRequest = pullRequests.get(1);

        assertThat(mergedPullRequest.getGithubId()).isEqualTo(201L);
        assertThat(mergedPullRequest.getNumber()).isEqualTo(2);
        assertThat(mergedPullRequest.getTitle()).isEqualTo("Add analytics");
        assertThat(mergedPullRequest.getState()).isEqualTo("MERGED");
        assertThat(mergedPullRequest.getAuthorLogin()).isEqualTo("bob");
        assertThat(mergedPullRequest.getCreatedAt()).isEqualTo(now.minus(2, ChronoUnit.DAYS));
        assertThat(mergedPullRequest.getMergedAt())
                .isNotNull()
                .isEqualTo(now.minus(3, ChronoUnit.MINUTES))
                .isEqualTo(mergedPullRequest.getClosedAt());
        assertThat(mergedPullRequest.getAdditions()).isEqualTo(75);
        assertThat(mergedPullRequest.getDeletions()).isEqualTo(15);
        assertThat(mergedPullRequest.getChangedFiles()).isEqualTo(3);
    }

    @Test
    void forceSync_existingPullRequestIsUpdated_updatesPullRequestAndReturnsFreshAnalytics() throws Exception
    {
        stubInitialGithubResponses();

        mockMvc.perform(post("/api/repositories/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REPOSITORY_REQUEST)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pullRequestAnalytics.totalPullRequests").value(1))
                .andExpect(jsonPath("$.pullRequestAnalytics.openPullRequests").value(1))
                .andExpect(jsonPath("$.pullRequestAnalytics.mergedPullRequests").value(0));

        RepositoryEntity repository = repositoryRepository.findByGithubId(100L).orElseThrow();


        stubGraphql(
                "GetPullRequestPage",
                "[\"OPEN\", \"CLOSED\", \"MERGED\"]",
                "updated-pull-request.json"
        );

        stubGraphql(
                "GetPullRequestSizePage",
                "[\"OPEN\", \"CLOSED\", \"MERGED\"]",
                "updated-pull-request-size.json"
        );

        mockMvc.perform(post("/api/repositories/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REPOSITORY_REQUEST)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pullRequestAnalytics.totalPullRequests").value(1))
                .andExpect(jsonPath("$.pullRequestAnalytics.openPullRequests").value(0))
                .andExpect(jsonPath("$.pullRequestAnalytics.mergedPullRequests").value(1));

        assertThat(repositoryRepository.count()).isEqualTo(1);
        assertThat(pullRequestRepository.count()).isEqualTo(1);

        List<PullRequestEntity> pullRequests = pullRequestRepository.findAllByRepositoryId(repository.getId());

        assertThat(pullRequests.size()).isEqualTo(1);

        assertThat(pullRequestRepository.findAllByRepositoryId(repository.getId()))
                .singleElement()
                .satisfies(pullRequest -> {
                    assertThat(pullRequest.getNumber()).isEqualTo(1);
                    assertThat(pullRequest.getTitle()).isEqualTo("Add dashboard and filters");
                    assertThat(pullRequest.getState()).isEqualTo("MERGED");
                    assertThat(pullRequest.getAuthorLogin()).isEqualTo("alice");
                    assertThat(pullRequest.getCreatedAt()).isEqualTo(now.minus(2, ChronoUnit.DAYS));
                    assertThat(pullRequest.getUpdatedAt()).isEqualTo(now.minus(3, ChronoUnit.MINUTES));
                    assertThat(pullRequest.getMergedAt())
                            .isEqualTo(now.minus(3, ChronoUnit.MINUTES))
                            .isEqualTo(pullRequest.getClosedAt());
                    assertThat(pullRequest.getAdditions()).isEqualTo(75);
                    assertThat(pullRequest.getDeletions()).isEqualTo(15);
                    assertThat(pullRequest.getChangedFiles()).isEqualTo(3);
                });

    }

    @Test
    void analyze_savedDataIsExpired_syncsPullRequestAndReturnsFreshAnalytics() throws Exception
    {
        stubInitialGithubResponses();

        mockMvc.perform(post("/api/repositories/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REPOSITORY_REQUEST)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pullRequestAnalytics.totalPullRequests").value(1))
                .andExpect(jsonPath("$.pullRequestAnalytics.openPullRequests").value(1));

        RepositoryEntity repository = repositoryRepository.findByGithubId(100L).orElseThrow();
        Instant expiredSyncTime = now.minus(20, ChronoUnit.MINUTES);

        repository.setSummarySyncedAt(expiredSyncTime);
        repository.setSizeSyncedAt(expiredSyncTime);
        repositoryRepository.save(repository);

        github.resetRequests();

        stubGraphql(
                "GetPullRequestSizePage",
                "[\"OPEN\", \"CLOSED\", \"MERGED\"]",
                "open-pull-request-size.json"
        );

        mockMvc.perform(post("/api/repositories/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REPOSITORY_REQUEST)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pullRequestAnalytics.totalPullRequests").value(1))
                .andExpect(jsonPath("$.pullRequestAnalytics.openPullRequests").value(1));

        assertThat(repositoryRepository.count()).isEqualTo(1);

        repository = repositoryRepository.findByGithubId(100L).orElseThrow();
        assertThat(repository.getSummarySyncedAt()).isAfter(expiredSyncTime);
        assertThat(repository.getSizeSyncedAt()).isAfter(expiredSyncTime);

        github.verify(2, postRequestedFor(urlEqualTo("/graphql")));
    }

    @Test
    void analyze_savedDataIsExpiredAndPullRequestBecameMerged_syncsPullRequestsAndReturnsFreshAnalytics() throws Exception
    {
        stubInitialGithubResponses();

        mockMvc.perform(post("/api/repositories/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REPOSITORY_REQUEST)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pullRequestAnalytics.totalPullRequests").value(1))
                .andExpect(jsonPath("$.pullRequestAnalytics.openPullRequests").value(1));

        RepositoryEntity repository = repositoryRepository.findByGithubId(100L).orElseThrow();
        Instant expiredSyncTime = now.minus(20, ChronoUnit.MINUTES);

        repository.setSummarySyncedAt(expiredSyncTime);
        repository.setSizeSyncedAt(expiredSyncTime);
        repositoryRepository.save(repository);

        github.resetRequests();

        stubGraphql(
                "GetPullRequestPage",
                "[\"OPEN\", \"CLOSED\", \"MERGED\"]",
                "open-pull-request-became-merged-and-new-one-was-added.json"
        );

        stubGraphql(
                "GetPullRequestSizePage",
                "[\"OPEN\", \"CLOSED\", \"MERGED\"]",
                "open-pull-request-became-merged-and-new-one-was-added-sizes.json"
        );

        mockMvc.perform(post("/api/repositories/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REPOSITORY_REQUEST)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pullRequestAnalytics.totalPullRequests").value(2))
                .andExpect(jsonPath("$.pullRequestAnalytics.openPullRequests").value(1))
                .andExpect(jsonPath("$.pullRequestAnalytics.mergedPullRequests").value(1));

        assertThat(repositoryRepository.count()).isEqualTo(1);

        repository = repositoryRepository.findByGithubId(100L).orElseThrow();
        assertThat(repository.getSummarySyncedAt()).isAfter(expiredSyncTime);
        assertThat(repository.getSizeSyncedAt()).isAfter(expiredSyncTime);

        List<PullRequestEntity> pullRequests = pullRequestRepository.findAllByRepositoryId(repository.getId());
        assertThat(pullRequests.size()).isEqualTo(2);
        assertThat(pullRequests)
                .extracting(PullRequestEntity::getGithubId)
                .containsExactlyInAnyOrder(200L, 201L);

        assertThat(pullRequests)
                .filteredOn(pr -> pr.getGithubId().equals(200L))
                .singleElement()
                .satisfies(pr -> {
                    assertThat(pr.getNumber()).isEqualTo(1);
                    assertThat(pr.getTitle()).isEqualTo("Add dashboard");
                    assertThat(pr.getState()).isEqualTo("MERGED");
                    assertThat(pr.getAuthorLogin()).isEqualTo("alice");
                    assertThat(pr.getCreatedAt()).isEqualTo(now.minus(2, ChronoUnit.DAYS));
                    assertThat(pr.getUpdatedAt()).isEqualTo(now.minus(3, ChronoUnit.MINUTES));
                    assertThat(pr.getMergedAt())
                            .isEqualTo(now.minus(3, ChronoUnit.MINUTES))
                            .isEqualTo(pr.getClosedAt());
                    assertThat(pr.getAdditions()).isEqualTo(40);
                    assertThat(pr.getDeletions()).isEqualTo(10);
                    assertThat(pr.getChangedFiles()).isEqualTo(2);
                });

        assertThat(pullRequests)
                .filteredOn(pr -> pr.getGithubId().equals(201L))
                .singleElement()
                .satisfies(pr -> {
                    assertThat(pr.getNumber()).isEqualTo(2);
                    assertThat(pr.getTitle()).isEqualTo("Add analytics");
                    assertThat(pr.getState()).isEqualTo("OPEN");
                    assertThat(pr.getAuthorLogin()).isEqualTo("bob");
                    assertThat(pr.getCreatedAt()).isEqualTo(now.minus(2, ChronoUnit.DAYS));
                    assertThat(pr.getUpdatedAt()).isEqualTo(now.minus(3, ChronoUnit.MINUTES));
                    assertThat(pr.getAdditions()).isEqualTo(75);
                    assertThat(pr.getDeletions()).isEqualTo(15);
                    assertThat(pr.getChangedFiles()).isEqualTo(3);
                });

        github.verify(2, postRequestedFor(urlEqualTo("/graphql")));
    }

    @Test
    void analyze_githubReturnsTwoPages_savesPullRequestsAndSizesFromBothPages() throws Exception
    {
        stubRepository();

        stubGraphql(
                "GetPullRequestPage",
                "[\"OPEN\", \"CLOSED\", \"MERGED\"]",
                "pull-requests-first-page.json"
        );

        stubGraphql(
                "GetPullRequestPage",
                "[\"OPEN\", \"CLOSED\", \"MERGED\"]",
                "summary-page-2",
                "pull-requests-second-page.json"
        );

        stubGraphql(
                "GetPullRequestSizePage",
                "[\"OPEN\"]",
                "pull-request-sizes-first-page.json"
        );

        stubGraphql(
                "GetPullRequestSizePage",
                "[\"OPEN\"]",
                "size-page-2",
                "pull-request-sizes-second-page.json"
        );

        stubGraphql(
                "GetPullRequestSizePage",
                "[\"CLOSED\", \"MERGED\"]",
                "empty-page.json"
        );

        mockMvc.perform(post("/api/repositories/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REPOSITORY_REQUEST)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pullRequestAnalytics.totalPullRequests").value(2))
                .andExpect(jsonPath("$.pullRequestAnalytics.openPullRequests").value(2))
                .andExpect(jsonPath("$.pullRequestAnalytics.mergedPullRequests").value(0));

        assertThat(repositoryRepository.count()).isEqualTo(1);

        RepositoryEntity repository = repositoryRepository.findByGithubId(100L).orElseThrow();
        List<PullRequestEntity> pullRequests = pullRequestRepository.findAllByRepositoryId(repository.getId());

        assertThat(pullRequests)
                .extracting(PullRequestEntity::getGithubId)
                .containsExactlyInAnyOrder(200L, 201L);

        assertThat(pullRequests)
                .filteredOn(pullRequest -> pullRequest.getGithubId().equals(200L))
                .singleElement()
                .satisfies(pullRequest -> {
                    assertThat(pullRequest.getTitle()).isEqualTo("Add dashboard");
                    assertThat(pullRequest.getAdditions()).isEqualTo(40);
                    assertThat(pullRequest.getDeletions()).isEqualTo(10);
                    assertThat(pullRequest.getChangedFiles()).isEqualTo(2);
                });

        assertThat(pullRequests)
                .filteredOn(pullRequest -> pullRequest.getGithubId().equals(201L))
                .singleElement()
                .satisfies(pullRequest -> {
                    assertThat(pullRequest.getTitle()).isEqualTo("Add analytics");
                    assertThat(pullRequest.getAdditions()).isEqualTo(75);
                    assertThat(pullRequest.getDeletions()).isEqualTo(15);
                    assertThat(pullRequest.getChangedFiles()).isEqualTo(3);
                });

        github.verify(2, postRequestedFor(urlEqualTo("/graphql"))
                .withRequestBody(matchingJsonPath("$.query", containing("GetPullRequestPage"))));
        github.verify(1, postRequestedFor(urlEqualTo("/graphql"))
                .withRequestBody(matchingJsonPath("$.query", containing("GetPullRequestPage")))
                .withRequestBody(matchingJsonPath("$.variables.cursor", equalTo("summary-page-2"))));

        github.verify(2, postRequestedFor(urlEqualTo("/graphql"))
                .withRequestBody(matchingJsonPath("$.query", containing("GetPullRequestSizePage")))
                .withRequestBody(matchingJsonPath("$.variables.states", equalToJson("[\"OPEN\"]", true, false))));
        github.verify(1, postRequestedFor(urlEqualTo("/graphql"))
                .withRequestBody(matchingJsonPath("$.query", containing("GetPullRequestSizePage")))
                .withRequestBody(matchingJsonPath("$.variables.cursor", equalTo("size-page-2"))));
    }

    private void stubInitialGithubResponses() throws IOException
    {
        stubRepository();
        stubGraphql("GetPullRequestPage", "[\"OPEN\", \"CLOSED\", \"MERGED\"]", "open-pull-request.json");
        stubGraphql("GetPullRequestSizePage", "[\"OPEN\"]", "open-pull-request-size.json");
        stubGraphql("GetPullRequestSizePage", "[\"CLOSED\", \"MERGED\"]", "empty-page.json");
    }

    private void stubRepository() throws IOException
    {
        github.stubFor(get(urlEqualTo("/repos/someuser/somerepo"))
                .willReturn(okJson(readResponse("repository.json"))));
    }

    private void stubGraphql(String operationName, String states, String responseFile) throws IOException
    {
        stubGraphql(operationName, states, null, responseFile);
    }

    private void stubGraphql(String operationName, String states, String cursor, String responseFile) throws IOException
    {
        github.stubFor(WireMock.post(urlEqualTo("/graphql"))
                .withRequestBody(matchingJsonPath("$.query", containing(operationName)))
                .withRequestBody(matchingJsonPath("$.variables.owner", equalTo("someuser")))
                .withRequestBody(matchingJsonPath("$.variables.name", equalTo("somerepo")))
                .withRequestBody(matchingJsonPath("$.variables.states", equalToJson(states, true, false)))
                .withRequestBody(matchingJsonPath("$.variables.cursor", cursor == null ? absent() : equalTo(cursor)))
                .willReturn(okJson(readResponse(responseFile))));
    }

    private String readResponse(String filename) throws IOException
    {
        return new ClassPathResource("github/" + filename).getContentAsString(UTF_8)
                .replace("CREATED_AT", now.minus(2, ChronoUnit.DAYS).toString())
                .replace("UPDATED_AT", now.minus(3, ChronoUnit.MINUTES).toString())
                .replace("CLOSED_AT", now.minus(3, ChronoUnit.MINUTES).toString())
                .replace("MERGED_AT", now.minus(3, ChronoUnit.MINUTES).toString());
    }
}
