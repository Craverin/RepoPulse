import { request } from "./httpClient"
import type { PullRequestAnalyticsResponse } from "./pullRequestAnalyticsApi"

interface AnalyzeRepositoryRequest {
  repositoryUrl: string
}

export function analyzeRepository(repositoryUrl: string): Promise<PullRequestAnalyticsResponse> {
  const body: AnalyzeRepositoryRequest = { repositoryUrl }

  return request<PullRequestAnalyticsResponse>("/api/repositories/analyze", {
    method: "POST",
    body
  })
}

export function syncRepository(repositoryUrl: string): Promise<PullRequestAnalyticsResponse> {
  const body: AnalyzeRepositoryRequest = { repositoryUrl }

  return request<PullRequestAnalyticsResponse>("/api/repositories/sync", {
    method: "POST",
    body
  })
}
