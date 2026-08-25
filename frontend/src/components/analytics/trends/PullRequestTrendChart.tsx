import { useState } from "react"
import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis
} from "recharts"
import type { PullRequestMonthlyMetrics } from "../../../api/pullRequestAnalyticsApi"

type TrendChartView = "flow" | "backlog" | "merge-rate" | "merge-time"
type TrendValueKey =
  | "pullRequestsCreated"
  | "pullRequestsMerged"
  | "pullRequestsClosedWithoutMerge"
  | "openPullRequestsAtMonthEnd"
  | "mergeRatePercent"
  | "medianMergeTimeHours"
type TrendValueUnit = "count" | "percent" | "hours"

interface PullRequestTrendChartProps {
  metrics: PullRequestMonthlyMetrics[]
}

interface TrendSeries {
  key: TrendValueKey
  label: string
  color: string
}

interface TrendChartConfig {
  label: string
  title: string
  description: string
  unit: TrendValueUnit
  series: TrendSeries[]
}

const compactNumberFormatter = new Intl.NumberFormat("en", {
  notation: "compact",
  maximumFractionDigits: 1
})

const monthFormatter = new Intl.DateTimeFormat("en", {
  month: "short",
  year: "2-digit",
  timeZone: "UTC"
})

const fullMonthFormatter = new Intl.DateTimeFormat("en", {
  month: "long",
  year: "numeric",
  timeZone: "UTC"
})

const chartConfigs: Record<TrendChartView, TrendChartConfig> = {
  flow: {
    label: "Flow",
    title: "Pull request flow",
    description: "Created and completed pull requests in each month",
    unit: "count",
    series: [
      { key: "pullRequestsCreated", label: "Created", color: "#6172f3" },
      { key: "pullRequestsMerged", label: "Merged", color: "#17b26a" },
      {
        key: "pullRequestsClosedWithoutMerge",
        label: "Closed without merge",
        color: "#98a2b3"
      }
    ]
  },
  backlog: {
    label: "Backlog",
    title: "Open backlog",
    description: "Pull requests that remained open at the end of each month",
    unit: "count",
    series: [
      {
        key: "openPullRequestsAtMonthEnd",
        label: "Open at month end",
        color: "#f79009"
      }
    ]
  },
  "merge-rate": {
    label: "Merge rate",
    title: "Monthly merge rate",
    description: "Share of completed pull requests that were merged",
    unit: "percent",
    series: [{ key: "mergeRatePercent", label: "Merge rate", color: "#17b26a" }]
  },
  "merge-time": {
    label: "Merge time",
    title: "Median merge time",
    description: "Median lifetime of pull requests merged in each month",
    unit: "hours",
    series: [{ key: "medianMergeTimeHours", label: "Median merge time", color: "#7f56d9" }]
  }
}

export function PullRequestTrendChart({ metrics }: PullRequestTrendChartProps) {
  const [activeView, setActiveView] = useState<TrendChartView>("flow")
  const config = chartConfigs[activeView]
  const hasValues = metrics.some((metric) =>
    config.series.some((series) => metric[series.key] !== null)
  )

  return (
    <article className="trends-panel trends-chart-panel">
      <div className="trends-chart-panel__topline">
        <div>
          <h2>{config.title}</h2>
          <p>{config.description}</p>
        </div>

        <div className="trends-chart-switcher">
          {(Object.entries(chartConfigs) as [TrendChartView, TrendChartConfig][]).map(
            ([view, viewConfig]) => (
              <button
                className={activeView === view ? "trends-chart-switcher__active" : ""}
                key={view}
                type="button"
                onClick={() => setActiveView(view)}
              >
                {viewConfig.label}
              </button>
            )
          )}
        </div>
      </div>

      <div className="trends-chart-legend">
        {config.series.map((series) => (
          <span key={series.key}>
            <i style={{ background: series.color }} />
            {series.label}
          </span>
        ))}
      </div>

      {hasValues ? (
        <div className="trends-chart">
          <ResponsiveContainer>
            <LineChart data={metrics} margin={{ top: 12, right: 12, bottom: 0, left: -10}}>
              <CartesianGrid vertical={false} stroke="#eaecf0" strokeDasharray="3 3" />
              <XAxis
                dataKey="month"
                axisLine={false}
                tickLine={false}
                tickFormatter={formatShortMonth}
                minTickGap={25}
                tick={{ fill: "#98a2b3", fontSize: 11 }}
              />
              <YAxis
                axisLine={false}
                tickLine={false}
                width={50}
                allowDecimals={config.unit !== "count"}
                domain={config.unit === "percent" ? [0, 100] : [0, "auto"]}
                tickFormatter={(value: number) => formatAxisValue(value, config.unit)}
                tick={{ fill: "#98a2b3", fontSize: 11 }}
              />
              <Tooltip
                cursor={{ stroke: "#d0d5dd", strokeDasharray: "4 4" }}
                contentStyle={{
                  border: "1px solid #e4e7ec",
                  borderRadius: 9,
                  boxShadow: "0 8px 20px rgb(16 24 40 / 10%)",
                  fontSize: 12
                }}
                labelFormatter={(month) => formatFullMonth(String(month))}
                formatter={(value, name) => [
                  formatTooltipValue(Number(value), config.unit),
                  String(name)
                ]}
              />

              {config.series.map((series) => (
                <Line
                  key={series.key}
                  type="monotone"
                  dataKey={series.key}
                  name={series.label}
                  stroke={series.color}
                  strokeWidth={2.25}
                  connectNulls={false}
                  dot={{ r: 2.75, fill: "#ffffff", strokeWidth: 2 }}
                  activeDot={{ r: 4, fill: "#ffffff", strokeWidth: 2.5 }}
                />
              ))}
            </LineChart>
          </ResponsiveContainer>
        </div>
      ) : (
        <div className="trends-chart__empty">
          No data is available for this metric in the selected period.
        </div>
      )}
    </article>
  )
}

function formatShortMonth(value: string): string {
  return monthFormatter.format(toMonthDate(value))
}

function formatFullMonth(value: string): string {
  return fullMonthFormatter.format(toMonthDate(value))
}

function toMonthDate(value: string): Date {
  const [year, month] = value.split("-").map(Number)
  return new Date(Date.UTC(year, month - 1, 1))
}

function formatAxisValue(value: number, unit: TrendValueUnit): string {
  if (unit === "percent") return `${value}%`
  if (unit === "hours") return `${compactNumberFormatter.format(value)}h`

  return compactNumberFormatter.format(value)
}

function formatTooltipValue(value: number, unit: TrendValueUnit): string {
  if (unit === "percent") return `${value.toFixed(1)}%`
  if (unit === "hours") return `${value.toFixed(1)} hours`

  return value.toLocaleString("en-US")
}
