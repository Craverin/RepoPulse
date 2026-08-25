import { useState } from "react"

import {
  Bar,
  BarChart,
  CartesianGrid,
  Rectangle,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis
} from "recharts"

import type {
  PullRequestSizeCategory,
  PullRequestSizeCategoryMetrics
} from "../../../api/pullRequestAnalyticsApi.ts"


type SizeChartView = "distribution" | "merge-time" | "merge-rate" | "stale-rate"
type SizeMetricKey =
  | "completedSharePercent"
  | "medianMergeTimeHours"
  | "mergeRatePercent"
  | "staleOpenPullRequestRatePercent"
type SizeMetricUnit = "percent" | "hours"

interface PullRequestSizeChartProps {
  metrics: PullRequestSizeCategoryMetrics[]
}

interface SizeChartConfig {
  label: string
  title: string
  description: string
  dataKey: SizeMetricKey
  tooltipName: string
  unit: SizeMetricUnit
}

const compactNumberFormatter = new Intl.NumberFormat("en", {
  notation: "compact",
  maximumFractionDigits: 1
})

const categoryLabels: Record<PullRequestSizeCategory, string> = {
  SMALL: "Small",
  MEDIUM: "Medium",
  LARGE: "Large",
  ENORMOUS: "Enormous"
}

const categoryColors: Record<PullRequestSizeCategory, string> = {
  SMALL: "#6172f3",
  MEDIUM: "#7f56d9",
  LARGE: "#f79009",
  ENORMOUS: "#f04438"
}

const chartConfigs: Record<SizeChartView, SizeChartConfig> = {
  distribution: {
    label: "Distribution",
    title: "Completed pull request distribution",
    description: "Share of completed pull requests in each size category",
    dataKey: "completedSharePercent",
    tooltipName: "Completed share",
    unit: "percent"
  },
  "merge-time": {
    label: "Merge time",
    title: "Median merge time by size",
    description: "Typical lifetime of merged pull requests in each category",
    dataKey: "medianMergeTimeHours",
    tooltipName: "Median merge time",
    unit: "hours"
  },
  "merge-rate": {
    label: "Merge rate",
    title: "Merge rate by size",
    description: "Share of completed pull requests that were merged",
    dataKey: "mergeRatePercent",
    tooltipName: "Merge rate",
    unit: "percent"
  },
  "stale-rate": {
    label: "Stale rate",
    title: "Stale open rate by size",
    description: "Share of ready-for-review open pull requests that are stale",
    dataKey: "staleOpenPullRequestRatePercent",
    tooltipName: "Stale open rate",
    unit: "percent"
  }
}

export function PullRequestSizeChart({ metrics }: PullRequestSizeChartProps) {
  const [activeView, setActiveView] = useState<SizeChartView>("distribution")
  const config = chartConfigs[activeView]
  const hasValues = metrics.some(
    (metric) => metric[config.dataKey] !== null
  )

  return (
    <article className="size-panel size-chart-panel">
      <div className="size-chart-panel__topline">
        <div>
          <h2>{config.title}</h2>
          <p>{config.description}</p>
        </div>

        <div className="size-chart-switcher">
          {(Object.entries(chartConfigs) as [SizeChartView, SizeChartConfig][]).map(
            ([view, viewConfig]) => (
              <button
                className={activeView === view ? "size-chart-switcher__active" : ""}
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

      {hasValues ? (
        <div className="size-chart">
          <ResponsiveContainer>
            <BarChart data={metrics} margin={{ top: 12, right: 10, bottom: 0, left: -8 }}>
              <CartesianGrid vertical={false} stroke="#eaecf0" strokeDasharray="3 3" />
              <XAxis
                dataKey="category"
                axisLine={false}
                tickLine={false}
                tickFormatter={(value: string) => formatCategory(value)}
                tick={{ fill: "#667085", fontSize: 11 }}
              />
              <YAxis
                axisLine={false}
                tickLine={false}
                width={50}
                domain={config.unit === "percent" ? [0, 100] : [0, "auto"]}
                tickFormatter={(value: number) => formatAxisValue(value, config.unit)}
                tick={{ fill: "#98a2b3", fontSize: 11 }}
              />
              <Tooltip
                cursor={{ fill: "#f2f4f7" }}
                contentStyle={{
                  border: "1px solid #e4e7ec",
                  borderRadius: 9,
                  boxShadow: "0 8px 20px rgb(16 24 40 / 10%)",
                  fontSize: 12
                }}
                labelFormatter={(category) => formatCategory(String(category))}
                formatter={(value, name) => [
                  formatTooltipValue(Number(value), config.unit),
                  String(name)
                ]}
              />
              <Bar
                dataKey={config.dataKey}
                name={config.tooltipName}
                maxBarSize={58}
                radius={[6, 6, 2, 2]}
                shape={(props) => {
                  const metric = props.payload as PullRequestSizeCategoryMetrics

                  return <Rectangle {...props} fill={categoryColors[metric.category]} />
                }}
              >
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </div>
      ) : (
        <div className="size-chart__empty">
          No data is available for this metric in the analyzed period.
        </div>
      )}
    </article>
  )
}

function formatCategory(value: string) {
  return categoryLabels[value as PullRequestSizeCategory]
}

function formatAxisValue(value: number, unit: SizeMetricUnit) {
  const formattedValue = compactNumberFormatter.format(value)

  return unit === "percent" ? `${formattedValue}%` : `${formattedValue}h`
}

function formatTooltipValue(value: number, unit: SizeMetricUnit) {
  return unit === "percent"
    ? `${value.toFixed(1)}%`
    : `${value.toFixed(1)} hours`
}
