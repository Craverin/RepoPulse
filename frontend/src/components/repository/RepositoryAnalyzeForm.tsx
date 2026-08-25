import { useState } from "react"
import "./RepositoryAnalyzeForm.css"

import type { SubmitEvent } from "react"

interface RepositoryAnalyzeFormProps {
  isLoading: boolean
  error: string | null
  onSubmit: (repositoryUrl: string) => Promise<void>
}

export function RepositoryAnalyzeForm({ isLoading, error, onSubmit }: RepositoryAnalyzeFormProps) {
  const [repositoryUrl, setRepositoryUrl] = useState("")

  function handleSubmit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault()

    const normalizedUrl = repositoryUrl.trim()
    if (normalizedUrl.length === 0 || isLoading) return

    onSubmit(normalizedUrl)
  }

  return (
    <div className="repository-form-wrapper">
      <form className="repository-form" onSubmit={handleSubmit}>
        <div className="repository-form__control">
          <span className="repository-form__icon" aria-hidden="true">
            <svg viewBox="0 0 24 24">
              <path d="M4 7.5A2.5 2.5 0 0 1 6.5 5h4l2 2h5A2.5 2.5 0 0 1 20 9.5v7A2.5 2.5 0 0 1 17.5 19h-11A2.5 2.5 0 0 1 4 16.5v-9Z" />
            </svg>
          </span>

          <input
            name="repository-url"
            type="url"
            value={repositoryUrl}
            onChange={(event) => setRepositoryUrl(event.target.value)}
            placeholder="https://github.com/spring-projects/spring-boot"
            autoComplete="url"
            disabled={isLoading}
          />

          <button type="submit" disabled={isLoading || repositoryUrl.trim().length === 0}>
            {isLoading ? (
              <>
                <span className="repository-form__spinner"/>
                Analyzing
              </>
            ) : (
              "Analyze repository"
            )}
          </button>
        </div>

        {error === null ? (
          <p className="repository-form__hint" id="repository-form-hint">
            Public repositories are supported. Initial synchronization can take a little longer.
          </p>
        ) : (
          <p className="repository-form__error" id="repository-form-error">
            {error}
          </p>
        )}
      </form>
    </div>
  )
}
