import { request } from "./httpClient.ts"

export interface PullRequestAnalyticsResponse {
  repositoryId: number
  owner: string
  name: string
  htmlUrl: string
  dataLastSyncedAt: string
  pullRequestAnalytics: PullRequestAnalytics
}

interface PullRequestAnalytics {
  totalPullRequests: number
  openPullRequests: number
  openDraftPullRequests: number
  mergedPullRequests: number
  closedWithoutMergePullRequests: number

  mergeRatePercent: number | null
  averageMergeTimeHours: number | null
  medianMergeTimeHours: number | null

  freshOpenPullRequests: number
  agingOpenPullRequests: number
  staleOpenPullRequests: number
  veryStaleOpenPullRequests: number
  staleOpenPullRequestRatePercent: number | null

  createdLast30Days: number
  mergedLast30Days: number
  closedWithoutMergeLast30Days: number
  uniquePullRequestAuthors: number

  stalestPullRequests: StalePullRequestResponse[]
}

interface StalePullRequestResponse {
  number: number
  title: string
  htmlUrl: string
  authorLogin: string
  inactiveDays: number
}

// Trends

interface PullRequestTrendsResponse {
  repositoryId: number
  dataLastSyncedAt: string
  monthlyMetrics: PullRequestMonthlyMetrics[]
}

interface PullRequestMonthlyMetrics {
  month: string
  pullRequestsCreated: number
  pullRequestsMerged: number
  pullRequestsClosedWithoutMerge: number
  openPullRequestsAtMonthEnd: number
  medianMergeTimeHours: number | null
}

// Insights

type InsightSeverity = "INFO" | "WARNING" | "CRITICAL"

type InsightValueUnit = "COUNT" | "HOURS" | "PERCENT" | "COEFFICIENT"

type PullRequestInsightType =
  | "BACKLOG_GROWING"
  | "MEDIAN_MERGE_TIME_HIGH"
  | "MEDIAN_MERGE_TIME_INCREASED"
  | "MERGE_RATE_LOW"
  | "MERGE_RATE_DROPPED"
  | "MERGE_THROUGHPUT_DROPPED"
  | "OVERSIZED_PULL_REQUEST_SHARE_HIGH"
  | "OVERSIZED_MERGE_TIME_HIGHER"
  | "OVERSIZED_MERGE_RATE_LOWER"
  | "SIZE_MERGE_TIME_ASSOCIATION"
  | "STALE_PULL_REQUEST_RATE_HIGH"
  | "STALE_OVERSIZED_PULL_REQUESTS"

interface PullRequestInsightsResponse {
  repositoryId: number
  dataLastSyncedAt: string
  insights: PullRequestInsight[]
}

interface PullRequestInsight {
  type: PullRequestInsightType
  severity: InsightSeverity
  description: string
  currentValue: number | null
  previousValue: number | null
  valueUnit: InsightValueUnit
}

// Size analytics

type PullRequestSizeCategory = "SMALL" | "MEDIUM" | "LARGE" | "ENORMOUS"

interface PullRequestSizeAnalyticsResponse {
  repositoryId: number
  periodStart: string
  periodEnd: string
  dataLastSyncedAt: string
  analytics: PullRequestSizeAnalytics
}

interface PullRequestSizeAnalytics {
  sizeStatistics: PullRequestSizeStatistics
  categoryMetrics: PullRequestSizeCategoryMetrics[]
  sizeImpact: PullRequestSizeImpact
  oversizedOpenPullRequests: OversizedOpenPullRequest[]
}

interface PullRequestSizeStatistics {
  completedPullRequests: number
  medianChangedLines: number | null
  medianChangedFiles: number | null
  p90ChangedLines: number | null
  p90ChangedFiles: number | null
}

interface PullRequestSizeCategoryMetrics {
  category: PullRequestSizeCategory

  completedPullRequests: number
  completedSharePercent: number | null

  mergedPullRequests: number
  closedWithoutMergePullRequests: number
  mergeRatePercent: number | null
  medianMergeTimeHours: number | null

  nonDraftOpenPullRequests: number
  nonDraftStaleOpenPullRequests: number
  staleOpenPullRequestRatePercent: number | null
}

interface PullRequestSizeImpact {
  oversizedToNonOversizedMedianMergeTimeRatio: number | null
  correlationSampleSize: number | null
  changedLinesToMergeTimeCorrelation: number | null
  changedFilesToMergeTimeCorrelation: number | null
}

interface OversizedOpenPullRequest {
  number: number
  title: string
  htmlUrl: string
  authorLogin: string
  draft: boolean
  changedLines: number
  changedFiles: number
  ageDays: number
  inactiveDays: number
}

function getPullRequestBasePath(repositoryId: number): string {
  return `/api/repositories/${repositoryId}/analytics/pull-requests`
}

export function getPullRequestOverview(
  repositoryId: number
): Promise<PullRequestAnalyticsResponse> {
  return request<PullRequestAnalyticsResponse>(getPullRequestBasePath(repositoryId))
}

export function getPullRequestTrends(
  repositoryId: number,
  months = 12
): Promise<PullRequestTrendsResponse> {
  const searchParams = new URLSearchParams({ months: months.toString() })

  return request<PullRequestTrendsResponse>(
    `${getPullRequestBasePath(repositoryId)}/trends?${searchParams}`
  )
}

export function getPullRequestInsights(repositoryId: number): Promise<PullRequestInsightsResponse> {
  return request<PullRequestInsightsResponse>(`${getPullRequestBasePath(repositoryId)}/insights`)
}

export function getPullRequestSizeAnalytics(
  repositoryId: number
): Promise<PullRequestSizeAnalyticsResponse> {
  return request<PullRequestSizeAnalyticsResponse>(`${getPullRequestBasePath(repositoryId)}/sizes`)
}
