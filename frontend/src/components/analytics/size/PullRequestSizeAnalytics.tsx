import { useEffect, useState } from "react"
import {
  getPullRequestInsights,
  getPullRequestSizeAnalytics
} from "../../../api/pullRequestAnalyticsApi.ts"

import type {
  OversizedOpenPullRequest,
  PullRequestInsight,
  PullRequestInsightsResponse,
  PullRequestSizeAnalyticsResponse,
  PullRequestSizeCategory,
  PullRequestSizeCategoryMetrics,
  PullRequestSizeImpact
} from "../../../api/pullRequestAnalyticsApi.ts"

import { insightTitles } from "../insights/pullRequestInsightPresentation.ts"
import { PullRequestSizeChart } from "./PullRequestSizeChart.tsx"
import "./PullRequestSizeAnalytics.css"


interface PullRequestSizeAnalyticsProps {
  repositoryId: number
}

interface SizeSummaryCardProps {
  label: string
  value: string
  detail: string
  helpText?: string
}

interface SizeQueueMetricProps {
  label: string
  value: string
  tone?: "neutral" | "warning" | "danger"
}

interface CategoryPresentation {
  label: string
  range: string
}

const integerFormatter = new Intl.NumberFormat("en-US")
const decimalFormatter = new Intl.NumberFormat("en-US", {
  maximumFractionDigits: 1
})

const dateFormatter = new Intl.DateTimeFormat("en", {
  day: "numeric",
  month: "short",
  year: "numeric",
  timeZone: "UTC"
})

const categoryOrder: PullRequestSizeCategory[] = ["SMALL", "MEDIUM", "LARGE", "ENORMOUS"]

const categoryPresentation: Record<PullRequestSizeCategory, CategoryPresentation> = {
  SMALL: { label: "Small", range: "0–99 changed lines" },
  MEDIUM: { label: "Medium", range: "100–499 changed lines" },
  LARGE: { label: "Large", range: "500–999 changed lines" },
  ENORMOUS: { label: "Enormous", range: "1,000+ changed lines" }
}

export default function PullRequestSizeAnalytics({ repositoryId }: PullRequestSizeAnalyticsProps) {
  const [response, setResponse] = useState<PullRequestSizeAnalyticsResponse | null>(null)
  const [insights, setInsights] = useState<PullRequestInsightsResponse | null>(null)
  const [sizeError, setSizeError] = useState<string | null>(null)
  const [insightsError, setInsightsError] = useState<string | null>(null)
  const [isSizeLoading, setIsSizeLoading] = useState(true)
  const [isInsightsLoading, setIsInsightsLoading] = useState(true)
  const [sizeReloadTrigger, setSizeReloadTrigger] = useState(0)
  const [insightsReloadTrigger, setInsightsReloadTrigger] = useState(0)

  useEffect(() => {
    getPullRequestSizeAnalytics(repositoryId)
      .then(setResponse)
      .catch((error) =>
        setSizeError(getErrorMessage(error, "Unable to load size analytics"))
      )
      .finally(() => setIsSizeLoading(false))
  }, [repositoryId, sizeReloadTrigger])

  useEffect(() => {
    getPullRequestInsights(repositoryId)
      .then(setInsights)
      .catch((error) =>
        setInsightsError(getErrorMessage(error, "Unable to load size insights"))
      )
      .finally(() => setIsInsightsLoading(false))
  }, [repositoryId, insightsReloadTrigger])

  function reloadSizeAnalytics() {
    setResponse(null)
    setSizeError(null)
    setIsSizeLoading(true)
    setSizeReloadTrigger((x) => x + 1)
  }

  function reloadInsights() {
    setInsights(null)
    setInsightsError(null)
    setIsInsightsLoading(true)
    setInsightsReloadTrigger((x) => x + 1)
  }

  if (response === null && isSizeLoading) return <SizeAnalyticsLoadingState />

  if (response === null) {
    return (
      <div className="size-error-state">
        <strong>Size analytics could not be loaded</strong>
        <p>{sizeError}</p>
        <button type="button" onClick={reloadSizeAnalytics}>
          Try again
        </button>
      </div>
    )
  }

  const analytics = response.analytics
  const statistics = analytics.sizeStatistics
  const impact = analytics.sizeImpact
  const categoryMetrics = analytics.categoryMetrics;

  return (
    <div className="pull-request-size-analytics">
      <section className="size-heading">
        <div>
          <p>Pull request changes</p>
          <h1>Size impact</h1>
          <span>
            {`Completed PRs from ${formatDate(response.periodStart)} to
            ${formatDate(response.periodEnd)}`}
          </span>
        </div>
      </section>

      <section className="size-summary">
        <SizeSummaryCard
          label="Completed sample"
          value={formatNumber(statistics.completedPullRequests)}
          detail="Completed pull requests analyzed"
        />
        <SizeSummaryCard
          label="Median change"
          value={formatChangedLines(statistics.medianChangedLines)}
          detail={`${formatChangedFiles(statistics.medianChangedFiles)} at the median`}
        />
        <SizeSummaryCard
          label="90th percentile"
          value={formatChangedLines(statistics.p90ChangedLines)}
          detail={`${formatChangedFiles(statistics.p90ChangedFiles)} at the 90th percentile`}
          helpText="The 90th percentile is a cutoff: 90% of completed pull requests
          are at or below the shown value, and only 10% are above it."
        />
        <SizeSummaryCard
          label="Oversized merge time"
          value={formatRatio(impact.oversizedToNonOversizedMedianMergeTimeRatio)}
          detail={formatRatioDetail(impact.oversizedToNonOversizedMedianMergeTimeRatio)}
        />
      </section>

      <section className="size-main-grid">
        <PullRequestSizeChart metrics={categoryMetrics} />
        <SizeInsightsPanel
          response={insights}
          error={insightsError}
          isLoading={isInsightsLoading}
          onRetry={reloadInsights}
        />
      </section>

      <SizeImpactPanel impact={impact} />
      <SizeCategoryTable metrics={categoryMetrics} />
      <OversizedOpenPullRequestsPanel pullRequests={analytics.oversizedOpenPullRequests} />
    </div>
  )
}

function SizeSummaryCard({ label, value, detail, helpText }: SizeSummaryCardProps) {
  return (
    <article className="size-summary-card">
      <span>{label}</span>

      {helpText !== undefined && (
        <button
          className="size-summary-card__help"
          type="button"
        >
          ?
          <span className="size-summary-card__tooltip">
            {helpText}
          </span>
        </button>
      )}

      <strong>{value}</strong>
      <p>{detail}</p>
    </article>
  )
}

function SizeInsightsPanel({
  response,
  error,
  isLoading,
  onRetry
}: {
  response: PullRequestInsightsResponse | null
  error: string | null
  isLoading: boolean
  onRetry: () => void
}) {
  const sizeInsights = response?.pullRequestInsights.filter(
      (insight) => insight.category === "SIZE_IMPACT"
    ) ?? []

  return (
    <aside className="size-panel size-insights">
      <div className="size-insights__heading">
        <div>
          <h2>Size signals</h2>
          <p>Notable patterns detected in size-related metrics</p>
        </div>
        {sizeInsights.length > 0 && <span>{sizeInsights.length}</span>}
      </div>

      {isLoading ? (
        <div className="size-insights__loading">
          <i />
          <i />
          <i />
        </div>
      ) : error !== null ? (
        <div className="size-insights__error">
          <p>{error}</p>
          <button type="button" onClick={onRetry}>
            Retry
          </button>
        </div>
      ) : sizeInsights.length === 0 ? (
        <div className="size-insights__empty">
          <span>✓</span>
          <strong>No notable size signals</strong>
          <p>None of the configured size thresholds were crossed.</p>
        </div>
      ) : (
        <div className="size-insights__list">
          {sizeInsights.map((insight) => (
            <SizeInsightItem insight={insight} key={insight.type} />
          ))}
        </div>
      )}
    </aside>
  )
}

function SizeInsightItem({ insight }: { insight: PullRequestInsight }) {
  return (
    <article className={`size-insight size-insight--${insight.severity.toLowerCase()}`}>
      <div>
        <strong>{insightTitles[insight.type]}</strong>
        <span>{insight.severity}</span>
      </div>
      <p>{insight.description}</p>
    </article>
  )
}

function SizeImpactPanel({ impact }: { impact: PullRequestSizeImpact }) {
  return (
    <section className="size-panel size-impact-panel">
      <div className="size-panel-heading">
        <div>
          <h2>Size and delivery time</h2>
          <p>How pull request size is associated with time to merge</p>
        </div>
        <span>{formatSampleSize(impact.correlationSampleSize)}</span>
      </div>

      <div className="size-impact-layout">
        <div className="size-impact-comparison">
          <div className="size-impact-comparison__metric">
            <span>Below 500 lines</span>
            <strong>{formatHours(impact.nonOversizedMedianMergeTimeHours)}</strong>
            <p>Median merge time</p>
          </div>

          <div className="size-impact-comparison__ratio">
            <span>{formatRatio(impact.oversizedToNonOversizedMedianMergeTimeRatio)}</span>
            <small>Merge-time ratio</small>
          </div>

          <div className="size-impact-comparison__metric">
            <span>500+ lines</span>
            <strong>{formatHours(impact.oversizedMedianMergeTimeHours)}</strong>
            <p>Median merge time</p>
          </div>
        </div>

        <div className="size-correlations">
          <CorrelationMetric
            label="Changed lines"
            value={impact.changedLinesToMergeTimeSpearmanCorrelation}
          />
          <CorrelationMetric
            label="Changed files"
            value={impact.changedFilesToMergeTimeSpearmanCorrelation}
          />
          <div className="size-correlation-scale">
            <span>−1 negative</span>
            <span>0 no monotonic association</span>
            <span>+1 positive</span>
          </div>
          <p className="size-correlations__note">
            Spearman correlation measures association, not causation. Positive values mean larger
            pull requests tend to take longer to merge.
          </p>
        </div>
      </div>
    </section>
  )
}

function CorrelationMetric({ label, value }: { label: string; value: number | null }) {
  const markerPosition = value === null
    ? 50
    : (value + 1) / 2 * 100

  return (
    <div className={`size-correlation${value === null ? " size-correlation--empty" : ""}`}>
      <div>
        <span>{label}</span>
        <strong>{formatCoefficient(value)}</strong>
      </div>
      <div className="size-correlation__track">
        <span style={{ left: `${markerPosition}%` }} />
      </div>
      <small>{formatCorrelationMeaning(value)}</small>
    </div>
  )
}

function SizeCategoryTable({ metrics }: { metrics: PullRequestSizeCategoryMetrics[] }) {
  return (
    <section className="size-panel size-category-panel">
      <div className="size-panel-heading">
        <div>
          <h2>Category breakdown</h2>
        </div>
      </div>

      <div className="size-category-table-wrapper">
        <table>
          <thead>
            <tr>
              <th>Size category</th>
              <th>Completed</th>
              <th>Share</th>
              <th>Merge rate</th>
              <th>Median merge time</th>
              <th>Open</th>
              <th>Stale open</th>
            </tr>
          </thead>
          <tbody>
            {metrics.map((metric) => {
              const presentation = categoryPresentation[metric.category]

              return (
                <tr key={metric.category}>
                  <td>
                    <div className="size-category-name">
                      <span className={`size-category-dot size-category-dot--${metric.category}`} />
                      <div>
                        <strong>{presentation.label}</strong>
                        <small>{presentation.range}</small>
                      </div>
                    </div>
                  </td>
                  <td>
                    <strong>{formatNumber(metric.completedPullRequests)}</strong>
                    <small>
                      {`${formatNumber(metric.mergedPullRequests)} merged ·
                      ${formatNumber(metric.closedWithoutMergePullRequests)} closed`}
                    </small>
                  </td>
                  <td>{formatPercent(metric.completedSharePercent)}</td>
                  <td>{formatPercent(metric.mergeRatePercent)}</td>
                  <td>{formatHours(metric.medianMergeTimeHours)}</td>
                  <td>{formatNumber(metric.nonDraftOpenPullRequests)}</td>
                  <td>
                    <strong>{formatNumber(metric.nonDraftStaleOpenPullRequests)}</strong>
                    <small>{formatPercent(metric.staleOpenPullRequestRatePercent)}</small>
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
    </section>
  )
}

function OversizedOpenPullRequestsPanel(
  { pullRequests }: { pullRequests: OversizedOpenPullRequest[] }
) {
  return (
    <section className="size-panel oversized-pull-requests">
      <div className="size-panel-heading">
        <div>
          <h2>Oversized open pull requests</h2>
          <p>Largest open pull requests with at least 500 changed lines</p>
        </div>
        {pullRequests.length > 0 && <span>{formatNumber(pullRequests.length)}</span>}
      </div>

      {pullRequests.length === 0 ? (
        <div className="oversized-pull-requests__empty">
          No oversized open pull requests were found.
        </div>
      ) : (
        <ul>
          {pullRequests.map((pullRequest) => (
            <li key={pullRequest.number}>
              <div className="oversized-pull-request__identity">
                <a href={pullRequest.htmlUrl} target="_blank">
                  {pullRequest.title}
                </a>
                <p>
                  #{pullRequest.number} by {pullRequest.authorLogin ?? "deleted account"}
                  {pullRequest.draft && <span>Draft</span>}
                </p>
              </div>

              <OversizedPullRequestMetric
                label="Changed lines"
                value={formatNumber(pullRequest.changedLines)}
              />
              <OversizedPullRequestMetric
                label="Changed files"
                value={formatNumber(pullRequest.changedFiles)}
              />
              <OversizedPullRequestMetric label="Age" value={formatDays(pullRequest.ageDays)} />
              <OversizedPullRequestMetric
                label="Inactive"
                value={formatDays(pullRequest.inactiveDays)}
                tone={getInactivityTone(pullRequest)}
              />
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

function OversizedPullRequestMetric({ label, value, tone = "neutral" }: SizeQueueMetricProps) {
  return (
    <div className={`oversized-pull-request__metric oversized-pull-request__metric--${tone}`}>
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  )
}

function SizeAnalyticsLoadingState() {
  return (
    <div className="size-loading">
      <div className="size-loading__heading" />
      <div className="size-loading__cards">
        <i />
        <i />
        <i />
        <i />
      </div>
      <div className="size-loading__main">
        <i />
        <i />
      </div>
    </div>
  )
}

function formatNumber(value: number) {
  return integerFormatter.format(value)
}

function formatChangedLines(value: number | null) {
  if (value === null) return "—";

  const formattedValue = decimalFormatter.format(value);
  return `${formattedValue} ${value === 1 ? "line" : "lines"}`
}

function formatChangedFiles(value: number | null) {
  if (value === null) return "No file data"

  const formattedValue = decimalFormatter.format(value)
  return `${formattedValue} ${value === 1 ? "file" : "files"}`
}

function formatPercent(value: number | null) {
  return value === null ? "—" : `${value.toFixed(1)}%`
}

function formatHours(value: number | null) {
  return value === null ? "—" : `${decimalFormatter.format(value)} h`
}

function formatRatio(value: number | null) {
  return value === null ? "—" : `${value.toFixed(2)}×`
}

function formatRatioDetail(value: number | null) {
  if (value === null) return "Needs at least 10 merged PRs in both groups"
  if (Math.abs(value - 1) < 0.05) return "Similar to PRs below 500 lines"

  return value > 1 ? "Slower than PRs below 500 lines" : "Faster than PRs below 500 lines"
}

function formatCoefficient(value: number | null) {
  return value === null ? "—" : value.toFixed(2)
}

function formatCorrelationMeaning(value: number | null) {
  if (value === null) return "Not enough merged pull requests"

  const absoluteValue = Math.abs(value)
  let strength: string

  if (absoluteValue < 0.2) strength = "Very weak"
  else if (absoluteValue < 0.4) strength = "Weak"
  else if (absoluteValue < 0.6) strength = "Moderate"
  else if (absoluteValue < 0.8) strength = "Strong"
  else strength = "Very strong"

  return `${strength} ${value > 0 ? "positive" : "negative"} association`
}

function formatSampleSize(value: number) {
  return `${formatNumber(value)} merged ${value === 1 ? "PR" : "PRs"} in correlation sample`
}

function formatDate(value: string): string {
  return dateFormatter.format(new Date(value))
}

function formatDays(value: number): string {
  return `${formatNumber(value)}d`
}

function getInactivityTone(
  pullRequest: OversizedOpenPullRequest
): "neutral" | "warning" | "danger" {
  if (pullRequest.draft) return "neutral"
  if (pullRequest.inactiveDays > 90) return "danger"
  if (pullRequest.inactiveDays > 30) return "warning"

  return "neutral"
}

function getErrorMessage(error: unknown, fallback: string) {
  if (error instanceof Error) return error.message

  return fallback
}
