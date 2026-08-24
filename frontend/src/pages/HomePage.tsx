import { useState } from "react"
import type { PullRequestAnalyticsResponse } from "../api/pullRequestAnalyticsApi"
import { analyzeRepository, syncRepository } from "../api/repositoriesApi"
import { PullRequestOverview } from "../components/analytics/PullRequestOverview"
import { RepositoryAnalyzeForm } from "../components/repository/RepositoryAnalyzeForm"
import "./HomePage.css"

const features = [
  {
    number: "01",
    title: "Delivery flow",
    description: "Merge rate, median merge time and recent pull request activity"
  },
  {
    number: "02",
    title: "Backlog health",
    description: "Fresh, aging and stale pull requests separated into useful groups"
  },
  {
    number: "03",
    title: "Size impact",
    description: "See how changed lines and files influence delivery time"
  }
]

export function HomePage() {
  const [overview, setOverview] = useState<PullRequestAnalyticsResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [isAnalyzing, setIsAnalyzing] = useState(false)
  const [isSyncing, setIsSyncing] = useState(false)

  async function handleAnalyze(repositoryUrl: string) {
    setError(null)
    setIsAnalyzing(true)

    try {
      const response = await analyzeRepository(repositoryUrl)
      setOverview(response)
    } catch (error) {
      setError(getErrorMessage(error))
    } finally {
      setIsAnalyzing(false)
    }
  }

  async function handleSync() {
    if (overview === null) return

    setError(null)
    setIsSyncing(true)

    try {
      const response = await syncRepository(overview.htmlUrl)
      setOverview(response)
    } catch (caughtError) {
      setError(getErrorMessage(caughtError))
    } finally {
      setIsSyncing(false)
    }
  }

  function handleAnalyzeAnother() {
    setOverview(null)
    setError(null)
  }

  return (
    <div className="home-page">
      <header className="home-page__header">
        <div className="home-page__header-inner">
          <a className="home-page__brand" href="/" aria-label="RepoPulse home">
            <span className="home-page__brand-mark" aria-hidden="true">
              <svg viewBox="0 0 28 28">
                <path d="M4 15h4l2.4-6 4.1 12 3.1-8 2 4H24" />
              </svg>
            </span>
            <span>RepoPulse</span>
          </a>

          <div className="home-page__product-label">
            <span aria-hidden="true" />
            GitHub pull request analytics
          </div>
        </div>
      </header>

      {overview === null ? (
        <main className="home-page__landing">
          <section className="home-page__hero" aria-labelledby="home-title">
            <p className="home-page__eyebrow">Repository health, in one view</p>
            <h1 id="home-title">Find where pull requests slow down</h1>
            <p className="home-page__intro">
              Paste a public GitHub repository URL. RepoPulse turns pull request history into a
              clear view of delivery speed, backlog health and size-related friction.
            </p>

            <RepositoryAnalyzeForm isLoading={isAnalyzing} error={error} onSubmit={handleAnalyze} />
          </section>

          <section className="home-page__features" aria-label="Available analytics">
            {features.map((feature) => (
              <article className="home-page__feature" key={feature.number}>
                <span className="home-page__feature-number">{feature.number}</span>
                <h2>{feature.title}</h2>
                <p>{feature.description}</p>
              </article>
            ))}
          </section>
        </main>
      ) : (
        <main className="home-page__dashboard">
          <PullRequestOverview
            data={overview}
            error={error}
            isSyncing={isSyncing}
            onAnalyzeAnother={handleAnalyzeAnother}
            onSync={handleSync}
          />
        </main>
      )}

      <footer className="home-page__footer">
        <span>RepoPulse</span>
        <span>Analytics calculated from GitHub repository data</span>
      </footer>
    </div>
  )
}

function getErrorMessage(error: unknown): string {
  if (error instanceof Error) return error.message

  return "Something went wrong while analyzing the repository"
}
