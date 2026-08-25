import { lazy, Suspense, useState } from "react"
import type { PullRequestAnalyticsResponse } from "../../api/pullRequestAnalyticsApi.ts"
import { PullRequestOverview } from "../analytics/overview/PullRequestOverview.tsx"
import "./RepositoryDashboard.css"

const PullRequestTrends = lazy(
  () => import("../analytics/trends/PullRequestTrends.tsx")
)

const PullRequestSizeAnalytics = lazy (
  () => import("../analytics/size/PullRequestSizeAnalytics.tsx")
)

interface RepositoryDashboardProps {
  data: PullRequestAnalyticsResponse
  error: string | null
  isSyncing: boolean
  onAnalyzeAnother: () => void
  onSync: () => Promise<void>
}

type DashboardSection = "overview" | "trends" | "size-impact"
const sections: { id: DashboardSection; label: string }[] = [
  { id: "overview", label: "Overview" },
  { id: "trends", label: "Trends" },
  { id: "size-impact", label: "Size impact" }
]

const dateFormatter = new Intl.DateTimeFormat("en", {
  dateStyle: "medium",
  timeStyle: "short"
})


export function RepositoryDashboard({
                                      data,
                                      error,
                                      isSyncing,
                                      onAnalyzeAnother,
                                      onSync
                                    }: RepositoryDashboardProps) {
  const [activeSection, setActiveSection] = useState<DashboardSection>("overview")

  return (
    <div className="repository-dashboard">
      <section className="dashboard-header">
        <div>
          <div className="dashboard-header__path">
            <span>{data.owner}</span>
            <span>/</span>
            <strong>{data.name}</strong>
          </div>

          <div className="dashboard-header__meta">
            <a href={data.htmlUrl} target="_blank">
              View on GitHub
              <svg viewBox="0 0 20 20">
                <path d="M7 13 13 7m-5 0h5v5" />
              </svg>
            </a>
            <span>·</span>
            <span>
              {"Synced "}
              <time dateTime={data.dataLastSyncedAt}>{formatSyncDate(data.dataLastSyncedAt)}</time>
            </span>
          </div>
        </div>

        <div className="dashboard-header__actions">
          <button
            className="dashboard-button dashboard-button--secondary"
            onClick={onAnalyzeAnother}
          >
            Analyze another
          </button>
          <button
            className="dashboard-button dashboard-button--primary"
            onClick={onSync}
            disabled={isSyncing}
          >
            {isSyncing ? (
              <>
                <span className="dashboard-button__spinner" />
                Syncing
              </>
            ) : (
              <>
                <svg viewBox="0 0 20 20">
                  <path d="M16 6V2m0 0h-4m4 0-3 3a6 6 0 1 0 1.5 6" />
                </svg>
                Sync now
              </>
            )}
          </button>
        </div>
      </section>

      {error !== null && (
        <div className="dashboard-error">
          <strong>Synchronization failed.</strong>
          <span>{error}</span>
        </div>
      )}

      <nav className="dashboard-navigation">
        {sections.map((section) => (
          <button
            className={activeSection === section.id ? "dashboard-navigation__item--active" : ""}
            key={section.id}
            type="button"
            onClick={() => setActiveSection(section.id)}
          >
            {section.label}
          </button>
        ))}
      </nav>

      {activeSection === "overview" ? (
        <PullRequestOverview data={data} />
      ) : (
        <Suspense fallback={<div className="dashboard-section-loading" />}>
          {activeSection === "trends" ? (
              <PullRequestTrends repositoryId={data.repositoryId} />
            ) : (
              <PullRequestSizeAnalytics repositoryId={data.repositoryId} />
          )}
        </Suspense>
      )}
    </div>
  )
}

function formatSyncDate(value: string): string {
  return dateFormatter.format(new Date(value))
}
