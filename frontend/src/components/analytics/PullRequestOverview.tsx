import type { PullRequestAnalyticsResponse } from "../../api/pullRequestAnalyticsApi.ts"
import "./PullRequestOverview.css"

interface PullRequestOverviewProps {
  data: PullRequestAnalyticsResponse
  error: string | null
  isSyncing: boolean
  onAnalyzeAnother: () => void
  onSync: () => Promise<void>
}

interface MetricCardProps {
  label: string
  value: string
  detail: string
  tone: "neutral" | "success" | "warning" | "danger"
}

interface ProgressRowProps {
  label: string
  value: number
  total: number
  tone: "blue" | "green" | "gray" | "amber" | "red" | "dark-red"
  hint?: string
}

const numberFormatter = new Intl.NumberFormat("en-US")
const dateFormatter = new Intl.DateTimeFormat("en", {
  dateStyle: "medium",
  timeStyle: "short"
})

export function PullRequestOverview({
  data,
  error,
  isSyncing,
  onAnalyzeAnother,
  onSync
}: PullRequestOverviewProps) {
  const analytics = data.pullRequestAnalytics
  const completedPullRequests =
    analytics.mergedPullRequests + analytics.closedWithoutMergePullRequests

  const stalePullRequests = analytics.staleOpenPullRequests + analytics.veryStaleOpenPullRequests

  return (
    <div className="pull-request-overview">
      <section className="overview-header">
        <div>
          <div className="overview-header__path">
            <span>{data.owner}</span>
            <span aria-hidden="true">/</span>
            <strong>{data.name}</strong>
          </div>

          <div className="overview-header__meta">
            <a href={data.htmlUrl} target="_blank">
              View on GitHub
              <svg viewBox="0 0 20 20" aria-hidden="true">
                <path d="M7 13 13 7m-5 0h5v5" />
              </svg>
            </a>
            <span aria-hidden="true">·</span>
            <span>
              {"Synced "}
              <time dateTime={data.dataLastSyncedAt}>{formatSyncDate(data.dataLastSyncedAt)}</time>
            </span>
          </div>
        </div>

        <div className="overview-header__actions">
          <button className="overview-button overview-button--secondary" onClick={onAnalyzeAnother}>
            Analyze another
          </button>
          <button
            className="overview-button overview-button--primary"
            onClick={() => onSync()}
            disabled={isSyncing}
          >
            {isSyncing ? (
              <>
                <span className="overview-button__spinner" aria-hidden="true" />
                Syncing
              </>
            ) : (
              <>
                <svg viewBox="0 0 20 20" aria-hidden="true">
                  <path d="M16 6V2m0 0h-4m4 0-3 3a6 6 0 1 0 1.5 6" />
                </svg>
                Sync now
              </>
            )}
          </button>
        </div>
      </section>

      {error !== null && (
        <div className="overview-error">
          <strong>Synchronization failed.</strong>
          <span>{error}</span>
        </div>
      )}

      <section className="overview-section">
        <div className="overview-section__heading">
          <div>
            <p>Pull requests</p>
            <h1 id="overview-title">Overview</h1>
          </div>
          <span>{formatPullRequestNumber(analytics.totalPullRequests)} analyzed</span>
        </div>

        <div className="overview-metrics">
          <MetricCard
            label="Open pull requests"
            value={formatNumber(analytics.openPullRequests)}
            detail={`${formatDraftNumber(analytics.openDraftPullRequests)}`}
            tone="neutral"
          />
          <MetricCard
            label="Merge rate"
            value={formatPercent(analytics.mergeRatePercent)}
            detail={`${formatPullRequestNumber(completedPullRequests, "completed")}`}
            tone="success"
          />
          <MetricCard
            label="Median merge time"
            value={formatHours(analytics.medianMergeTimeHours)}
            detail={`Average ${formatHours(analytics.averageMergeTimeHours)}`}
            tone="neutral"
          />
          <MetricCard
            label="Stale open rate"
            value={formatPercent(analytics.staleOpenPullRequestRatePercent)}
            detail={`${formatPullRequestNumber(stalePullRequests, "stale")}`}
            tone={getStaleMetricTone(
              analytics.staleOpenPullRequestRatePercent,
              stalePullRequests
            )}
          />
        </div>
      </section>

      <section className="overview-grid" aria-label="Pull request breakdown">
        <article className="overview-panel">
          <div className="overview-panel__heading">
            <div>
              <h2>Lifecycle</h2>
              <p>Current status across the repository</p>
            </div>
          </div>

          <div className="overview-progress-list">
            <ProgressRow
              label="Open"
              value={analytics.openPullRequests}
              total={analytics.totalPullRequests}
              tone="blue"
            />
            <ProgressRow
              label="Merged"
              value={analytics.mergedPullRequests}
              total={analytics.totalPullRequests}
              tone="green"
            />
            <ProgressRow
              label="Closed without merge"
              value={analytics.closedWithoutMergePullRequests}
              total={analytics.totalPullRequests}
              tone="gray"
            />
          </div>
        </article>

        <article className="overview-panel">
          <div className="overview-panel__heading">
            <div>
              <h2>Open backlog age</h2>
              <p>Grouped by time since the latest update</p>
            </div>
          </div>

          <div className="overview-progress-list">
            <ProgressRow
              label="Fresh"
              hint="0–7 days"
              value={analytics.freshOpenPullRequests}
              total={analytics.openPullRequests}
              tone="green"
            />
            <ProgressRow
              label="Aging"
              hint="8–30 days"
              value={analytics.agingOpenPullRequests}
              total={analytics.openPullRequests}
              tone="amber"
            />
            <ProgressRow
              label="Stale"
              hint="31–90 days"
              value={analytics.staleOpenPullRequests}
              total={analytics.openPullRequests}
              tone="red"
            />
            <ProgressRow
              label="Very stale"
              hint="90+ days"
              value={analytics.veryStaleOpenPullRequests}
              total={analytics.openPullRequests}
              tone="dark-red"
            />
          </div>
        </article>
      </section>

      <section className="overview-lower-grid">
        <article className="overview-panel overview-activity">
          <div className="overview-panel__heading">
            <div>
              <h2>Recent activity</h2>
              <p>Pull request outcomes over the last 30 days</p>
            </div>
          </div>

          <dl className="overview-activity__metrics">
            <div>
              <dt>Created</dt>
              <dd>{formatNumber(analytics.createdLast30Days)}</dd>
            </div>
            <div>
              <dt>Merged</dt>
              <dd>{formatNumber(analytics.mergedLast30Days)}</dd>
            </div>
            <div>
              <dt>Closed without merge</dt>
              <dd>{formatNumber(analytics.closedWithoutMergeLast30Days)}</dd>
            </div>
            <div>
              <dt>All-time authors</dt>
              <dd>{formatNumber(analytics.uniquePullRequestAuthors)}</dd>
            </div>
          </dl>
        </article>

        <article className="overview-panel overview-stale-list">
          <div className="overview-panel__heading">
            <div>
              <h2>Needs attention</h2>
              <p>Open pull requests with the longest inactivity</p>
            </div>
          </div>

          {analytics.stalestPullRequests.length === 0 ? (
            <div className="overview-empty-state">No stale open pull requests found.</div>
          ) : (
            <ul>
              {analytics.stalestPullRequests.map((pullRequest) => (
                <li key={pullRequest.number}>
                  <div className="overview-stale-list__content">
                    <div>
                      <a href={pullRequest.htmlUrl} target="_blank">
                        {pullRequest.title}
                      </a>
                      <p>
                        #{pullRequest.number} by {pullRequest.authorLogin}
                      </p>
                    </div>
                    <span>{formatDays(pullRequest.inactiveDays)}</span>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </article>
      </section>
    </div>
  )
}

function MetricCard({ label, value, detail, tone }: MetricCardProps) {
  return (
    <article className={`overview-metric overview-metric--${tone}`}>
      <div className="overview-metric__topline">
        <span>{label}</span>
        <span className="overview-metric__indicator" aria-hidden="true" />
      </div>
      <strong>{value}</strong>
      <p>{detail}</p>
    </article>
  )
}

function ProgressRow({ label, value, total, tone, hint }: ProgressRowProps) {
  const width = total === 0 ? 0 : value * 100 / total
  return (
    <div className="overview-progress">
      <div className="overview-progress__labels">
        <span>
          {label}
          {hint !== undefined && <small>{hint}</small>}
        </span>
        <strong>{formatNumber(value)}</strong>
      </div>
      <div
        className="overview-progress__track"
      >
        <span
          className={`overview-progress__bar overview-progress__bar--${tone}`}
          style={{ width: `${width}%` }}
        />
      </div>
    </div>
  )
}

function getStaleMetricTone(
  staleRatePercent: number | null,
  stalePullRequests: number
): MetricCardProps["tone"] {
  if (staleRatePercent === null) return "neutral"

  if (staleRatePercent >= 50 && stalePullRequests >= 5)
    return "danger"

  if (staleRatePercent >= 20 && stalePullRequests >= 2)
    return "warning"

  return "success"
}

function formatNumber(value: number): string {
  return numberFormatter.format(value)
}

function formatPullRequestNumber(value: number, type?: string): string {
  const formattedType = type === undefined ? "" : ` ${type}`
  return `${formatNumber(value)}${formattedType} ${value === 1 ? "pull request" : "pull requests"}`
}

function formatDraftNumber(value: number): string {
  return `${formatNumber(value)} ${value === 1 ? "draft" : "drafts"}`
}

function formatPercent(value: number | null): string {
  return value === null ? "—" : `${value.toFixed(1)}%`
}

function formatHours(value: number | null): string {
  return value === null ? "—" : `${value.toFixed(1)} h`
}

function formatDays(value: number): string {
  return `${formatNumber(value)} ${value === 1 ? "day" : "days"}`
}

function formatSyncDate(value: string): string {
  const date = new Date(value)

  return dateFormatter.format(date);
}
