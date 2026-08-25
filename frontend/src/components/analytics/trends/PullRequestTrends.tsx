import { useEffect, useState } from "react"
import { getPullRequestInsights, getPullRequestTrends } from "../../../api/pullRequestAnalyticsApi.ts"
import type {
  PullRequestInsight,
  PullRequestInsightsResponse,
  PullRequestMonthlyMetrics,
  PullRequestTrendsResponse
} from "../../../api/pullRequestAnalyticsApi.ts"

import { PullRequestTrendChart } from "./PullRequestTrendChart.tsx"
import "./PullRequestTrends.css"
import { insightTitles } from "../insights/pullRequestInsightPresentation.ts"

interface PullRequestTrendsProps {
  repositoryId: number
}

interface TrendSummaryCardProps {
  label: string
  value: string
  comparison: string
  comparisonTone: "neutral" | "positive" | "negative"
}

const numberFormatter = new Intl.NumberFormat("en-US")
const monthFormatter = new Intl.DateTimeFormat("en", {
  month: "short",
  year: "numeric",
  timeZone: "UTC"
})

const availablePeriods = [6, 12, 24]

export default function PullRequestTrends({ repositoryId }: PullRequestTrendsProps) {
  const [months, setMonths] = useState(12)
  const [trends, setTrends] = useState<PullRequestTrendsResponse | null>(null)
  const [insights, setInsights] = useState<PullRequestInsightsResponse | null>(null)
  const [trendsError, setTrendsError] = useState<string | null>(null)
  const [insightsError, setInsightsError] = useState<string | null>(null)
  const [isTrendsLoading, setIsTrendsLoading] = useState(true)
  const [isInsightsLoading, setIsInsightsLoading] = useState(true)
  const [trendsReloadTrigger, setTrendsReloadTrigger] = useState(0)
  const [insightsReloadTrigger, setInsightsReloadTrigger] = useState(0)


  useEffect(() => {
    getPullRequestTrends(repositoryId, months)
      .then((resp) => setTrends(resp))
      .catch((error) => setTrendsError(getErrorMessage(error, "Unable to load trends")))
      .finally(() => setIsTrendsLoading(false))
  }, [repositoryId, months, trendsReloadTrigger])

  useEffect(() => {
    getPullRequestInsights(repositoryId)
      .then((resp) => setInsights(resp))
      .catch((error) =>
        setInsightsError(getErrorMessage(error, "Unable to load insights"))
      )
      .finally(() => setIsInsightsLoading(false))
  }, [repositoryId, insightsReloadTrigger])

  function selectPeriod(period: number) {
    if (period === months) return

    setMonths(period)
    setTrends(null)
    setTrendsError(null)
    setIsTrendsLoading(true)
  }

  function reloadTrends() {
    setTrends(null)
    setTrendsError(null)
    setIsTrendsLoading(true)
    setTrendsReloadTrigger(x => x++)
  }

  function reloadInsights() {
    setIsInsightsLoading(true)
    setInsightsError(null)
    setInsightsReloadTrigger(x => x++)
  }


  if (trends === null && isTrendsLoading) return <TrendsLoadingState />

  if (trends === null) {
    return (
      <div className="trends-error-state">
        <strong>Trends could not be loaded</strong>
        <p>{trendsError}</p>
        <button type="button" onClick={reloadTrends}>
          Try again
        </button>
      </div>
    )
  }

  const metrics = trends.monthlyMetrics
  const latestMonth = metrics.at(-1)
  const previousMonth = metrics.at(-2)

  return (
    <div className="pull-request-trends">
      <section className="trends-heading">
        <div>
          <p>Pull request history</p>
          <h1>Trends</h1>
          <span>{formatPeriod(metrics.at(0)?.month, metrics.at(-1)?.month)}</span>
        </div>

        <div className="trends-period-selector">
          {availablePeriods.map((period) => (
            <button
              className={months === period ? "trends-period-selector__active" : ""}
              key={period}
              type="button"
              onClick={() => selectPeriod(period)}
            >
              {period}M
            </button>
          ))}
        </div>
      </section>

      {trendsError !== null && (
        <div className="trends-inline-error">
          <span>{trendsError}</span>
          <button type="button" onClick={reloadTrends}>
            Retry
          </button>
        </div>
      )}

      {latestMonth === undefined ? (
        <div className="trends-empty-state">
          There is no monthly pull request data for the selected period.
        </div>
      ) : (
        <>
          <section className="trends-summary">
            <TrendSummaryCard
              label="Created"
              value={formatNumber(latestMonth.pullRequestsCreated)}
              comparison={formatCountComparison(
                latestMonth.pullRequestsCreated,
                previousMonth?.pullRequestsCreated
              )}
              comparisonTone="neutral"
            />
            <TrendSummaryCard
              label="Merged"
              value={formatNumber(latestMonth.pullRequestsMerged)}
              comparison={formatCountComparison(
                latestMonth.pullRequestsMerged,
                previousMonth?.pullRequestsMerged
              )}
              comparisonTone={getComparisonTone(
                latestMonth.pullRequestsMerged,
                previousMonth?.pullRequestsMerged,
                "higher"
              )}
            />
            <TrendSummaryCard
              label="Open backlog"
              value={formatNumber(latestMonth.openPullRequestsAtMonthEnd)}
              comparison={formatCountComparison(
                latestMonth.openPullRequestsAtMonthEnd,
                previousMonth?.openPullRequestsAtMonthEnd
              )}
              comparisonTone={getComparisonTone(
                latestMonth.openPullRequestsAtMonthEnd,
                previousMonth?.openPullRequestsAtMonthEnd,
                "lower"
              )}
            />
            <TrendSummaryCard
              label="Median merge time"
              value={formatHours(latestMonth.medianMergeTimeHours)}
              comparison={formatHoursComparison(
                latestMonth.medianMergeTimeHours,
                previousMonth?.medianMergeTimeHours
              )}
              comparisonTone={getComparisonTone(
                latestMonth.medianMergeTimeHours,
                previousMonth?.medianMergeTimeHours,
                "lower"
              )}
            />
          </section>

          <section className="trends-main-grid">
            <PullRequestTrendChart metrics={metrics} />
            <TrendsInsightsPanel
              response={insights}
              error={insightsError}
              isLoading={isInsightsLoading}
              onRetry={reloadInsights}
            />
          </section>

          <MonthlyMetricsTable metrics={metrics} />
        </>
      )}
    </div>
  )
}

function TrendSummaryCard({ label, value, comparison, comparisonTone }: TrendSummaryCardProps) {
  return (
    <article className="trend-summary-card">
      <span>{label}</span>
      <strong>{value}</strong>
      <p className={`trend-summary-card__comparison--${comparisonTone}`}>{comparison}</p>
    </article>
  )
}

function TrendsInsightsPanel({
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
  const trendInsights = response?.pullRequestInsights
    .filter((insight) => insight.category !== "SIZE_IMPACT") ?? []

  return (
    <aside className="trends-panel trends-insights">
      <div className="trends-insights__heading">
        <div>
          <h2>Signals</h2>
          <p>Last 30 days compared with the previous 30</p>
        </div>
        {trendInsights.length > 0 && <span>{trendInsights.length}</span>}
      </div>

      {isLoading ? (
        <div className="trends-insights__loading">
          <i />
          <i />
          <i />
        </div>
      ) : error !== null ? (
        <div className="trends-insights__error">
          <p>{error}</p>
          <button type="button" onClick={onRetry}>
            Retry
          </button>
        </div>
      ) : trendInsights.length === 0 ? (
        <div className="trends-insights__empty">
          <span>✓</span>
          <strong>No notable signals</strong>
          <p>None of the configured thresholds were crossed.</p>
        </div>
      ) : (
        <div className="trends-insights__list">
          {trendInsights.map((insight) => (
            <InsightItem insight={insight} key={insight.type} />
          ))}
        </div>
      )}
    </aside>
  )
}

function InsightItem({ insight }: { insight: PullRequestInsight }) {
  return (
    <article className={`trend-insight trend-insight--${insight.severity.toLowerCase()}`}>
      <div>
        <span>
          {formatInsightCategory(insight.category)} · {insight.severity}
        </span>
        <strong>{insightTitles[insight.type]}</strong>
      </div>
      <p>{insight.description}</p>
    </article>
  )
}

function MonthlyMetricsTable({ metrics }: { metrics: PullRequestMonthlyMetrics[] }) {
  return (
    <section className="trends-panel trends-table-panel">
      <div className="trends-table-panel__heading">
        <div>
          <h2>Monthly breakdown</h2>
        </div>
      </div>

      <div className="trends-table-wrapper">
        <table>
          <thead>
            <tr>
              <th>Month</th>
              <th>Created</th>
              <th>Merged</th>
              <th>Closed</th>
              <th>Open backlog</th>
              <th>Merge rate</th>
              <th>Median merge time</th>
            </tr>
          </thead>
          <tbody>
            {metrics
              .slice()
              .reverse()
              .map((metric) => (
                <tr key={metric.month}>
                  <td>
                    <time dateTime={metric.month}>{formatMonth(metric.month)}</time>
                  </td>
                  <td>{formatNumber(metric.pullRequestsCreated)}</td>
                  <td>{formatNumber(metric.pullRequestsMerged)}</td>
                  <td>{formatNumber(metric.pullRequestsClosedWithoutMerge)}</td>
                  <td>{formatNumber(metric.openPullRequestsAtMonthEnd)}</td>
                  <td>{formatPercent(metric.mergeRatePercent)}</td>
                  <td>{formatHours(metric.medianMergeTimeHours)}</td>
                </tr>
              ))}
          </tbody>
        </table>
      </div>
    </section>
  )
}

function TrendsLoadingState() {
  return (
    <div className="trends-loading">
      <div className="trends-loading__heading" />
      <div className="trends-loading__cards">
        <i />
        <i />
        <i />
        <i />
      </div>
      <div className="trends-loading__content" />
    </div>
  )
}

function formatNumber(value: number): string {
  return numberFormatter.format(value)
}

function formatPercent(value: number | null): string {
  return value === null ? "—" : `${value.toFixed(1)}%`
}

function formatHours(value: number | null): string {
  return value === null ? "—" : `${value.toFixed(1)} h`
}

function formatCountComparison(current: number, previous: number | undefined): string {
  if (previous === undefined) return "No previous month comparison"

  const difference = current - previous
  if (difference === 0) return "No change from previous month"

  return `${difference > 0 ? "+" : ""}${formatNumber(difference)} from previous month`
}

function formatHoursComparison(
  current: number | null,
  previous: number | null | undefined
): string {
  if (current === null) return "No merged pull requests this month"
  if (previous === null || previous === undefined) return "No previous month comparison"

  const difference = current - previous
  if (Math.abs(difference) < 0.05) return "No change from previous month"

  return `${difference > 0 ? "+" : ""}${difference.toFixed(1)} h from previous month`
}

function getComparisonTone(
  current: number | null,
  previous: number | null | undefined,
  preferredDirection: "higher" | "lower"
): TrendSummaryCardProps["comparisonTone"] {
  if (
    current === null ||
    previous === null ||
    previous === undefined ||
    Math.abs(current - previous) < 0.05
  )
    return "neutral"

  const improved = preferredDirection === "higher" ? current > previous : current < previous
  return improved ? "positive" : "negative"
}

function formatPeriod(firstMonth: string | undefined, lastMonth: string | undefined) {
  if (firstMonth === undefined || lastMonth === undefined) return "No completed months"

  return `${formatMonth(firstMonth)} – ${formatMonth(lastMonth)}`
}

function formatMonth(value: string) {
  const [year, month] = value.split("-").map(Number)
  return monthFormatter.format(new Date(Date.UTC(year, month - 1, 1)))
}

function formatInsightCategory(category: PullRequestInsight["category"]) {
  if (category === "CURRENT_STATE") return "Current state"
  if (category === "PERIOD_COMPARISON") return "Period change"
}

function getErrorMessage(error: unknown, fallback: string) {
  if (error instanceof Error) return error.message

  return fallback
}
