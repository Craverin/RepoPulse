import type { PullRequestInsightType } from "../../../api/pullRequestAnalyticsApi"

export const insightTitles: Record<PullRequestInsightType, string> = {
  BACKLOG_GROWING: "Backlog is growing",
  MEDIAN_MERGE_TIME_HIGH: "Merge time is high",
  MEDIAN_MERGE_TIME_INCREASED: "Merge time increased",
  MERGE_RATE_LOW: "Merge rate is low",
  MERGE_RATE_DROPPED: "Merge rate dropped",
  MERGE_THROUGHPUT_DROPPED: "Merge throughput dropped",
  OVERSIZED_PULL_REQUEST_SHARE_HIGH: "Too many oversized pull requests",
  OVERSIZED_MERGE_TIME_HIGHER: "Oversized pull requests merge slower",
  OVERSIZED_MERGE_RATE_LOWER: "Oversized pull requests merge less often",
  SIZE_ASSOCIATED_WITH_LONGER_MERGE_TIME: "Size is linked to longer merge time",
  STALE_PULL_REQUEST_RATE_HIGH: "Stale pull request rate is high",
  STALE_OVERSIZED_PULL_REQUESTS: "Oversized pull requests need attention"
}