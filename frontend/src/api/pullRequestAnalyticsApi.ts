import { request } from "./httpClient.ts"

export interface PullRequestAnalyticsResponse {
  repositoryId: number
  owner: string
  name: string
  htmlUrl: string
  dataLastSyncedAt: string
  pullRequestAnalytics: PullRequestAnalytics
}

export interface PullRequestAnalytics {
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

export interface StalePullRequestResponse {
  number: number
  title: string
  htmlUrl: string
  authorLogin: string | null
  inactiveDays: number
}

// Trends

export interface PullRequestTrendsResponse {
  repositoryId: number
  dataLastSyncedAt: string
  monthlyMetrics: PullRequestMonthlyMetrics[]
}

export interface PullRequestMonthlyMetrics {
  month: string
  pullRequestsCreated: number
  pullRequestsMerged: number
  pullRequestsClosedWithoutMerge: number
  openPullRequestsAtMonthEnd: number
  mergeRatePercent: number | null
  medianMergeTimeHours: number | null
}

// Insights

export type InsightSeverity = "INFO" | "WARNING" | "CRITICAL"

export type InsightCategory = "CURRENT_STATE" | "PERIOD_COMPARISON" | "SIZE_IMPACT"

export type InsightValueUnit = "COUNT" | "HOURS" | "PERCENT" | "COEFFICIENT"

export type PullRequestInsightType =
  | "BACKLOG_GROWING"
  | "MEDIAN_MERGE_TIME_HIGH"
  | "MEDIAN_MERGE_TIME_INCREASED"
  | "MERGE_RATE_LOW"
  | "MERGE_RATE_DROPPED"
  | "MERGE_THROUGHPUT_DROPPED"
  | "OVERSIZED_PULL_REQUEST_SHARE_HIGH"
  | "OVERSIZED_MERGE_TIME_HIGHER"
  | "OVERSIZED_MERGE_RATE_LOWER"
  | "SIZE_ASSOCIATED_WITH_LONGER_MERGE_TIME"
  | "STALE_PULL_REQUEST_RATE_HIGH"
  | "STALE_OVERSIZED_PULL_REQUESTS"

export interface PullRequestInsightsResponse {
  repositoryId: number
  dataLastSyncedAt: string
  pullRequestInsights: PullRequestInsight[]
}

export interface PullRequestInsight {
  type: PullRequestInsightType
  category: InsightCategory
  severity: InsightSeverity
  description: string
  currentValue: number | null
  previousValue: number | null
  valueUnit: InsightValueUnit
}

// Size analytics

export type PullRequestSizeCategory = "SMALL" | "MEDIUM" | "LARGE" | "ENORMOUS"

export interface PullRequestSizeAnalyticsResponse {
  repositoryId: number
  owner: string
  name: string
  htmlUrl: string
  periodStart: string
  periodEnd: string
  dataLastSyncedAt: string
  analytics: PullRequestSizeAnalytics
}

export interface PullRequestSizeAnalytics {
  sizeStatistics: PullRequestSizeStatistics
  categoryMetrics: PullRequestSizeCategoryMetrics[]
  sizeImpact: PullRequestSizeImpact
  oversizedOpenPullRequests: OversizedOpenPullRequest[]
}

export interface PullRequestSizeStatistics {
  completedPullRequests: number
  medianChangedLines: number | null
  medianChangedFiles: number | null
  p90ChangedLines: number | null
  p90ChangedFiles: number | null
}

export interface PullRequestSizeCategoryMetrics {
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

export interface PullRequestSizeImpact {
  oversizedMedianMergeTimeHours: number | null
  nonOversizedMedianMergeTimeHours: number | null
  oversizedToNonOversizedMedianMergeTimeRatio: number | null
  correlationSampleSize: number
  changedLinesToMergeTimeSpearmanCorrelation: number | null
  changedFilesToMergeTimeSpearmanCorrelation: number | null
}

export interface OversizedOpenPullRequest {
  number: number
  title: string
  htmlUrl: string
  authorLogin: string | null
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
