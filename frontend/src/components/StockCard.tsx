import React, { useEffect, useRef, useState } from 'react'

// ── Types ─────────────────────────────────────────────────────────────────────

export interface IntradayPoint {
  time: string   // "HH:MM" (real data) or "9:30 AM" (mock fallback)
  price: number
}

export interface StockCardProps {
  symbol:    string
  lastPrice: number
  change:    number
  changePct: number
  data?:     IntradayPoint[]   // if omitted, mock data is generated
}

// ── Mock data generator ───────────────────────────────────────────────────────
// 14 points at 30-minute intervals from 9:30 AM to 4:00 PM ET

const MARKET_TIMES: string[] = [
  '9:30 AM',  '10:00 AM', '10:30 AM', '11:00 AM',
  '11:30 AM', '12:00 PM', '12:30 PM', '1:00 PM',
  '1:30 PM',  '2:00 PM',  '2:30 PM',  '3:00 PM',
  '3:30 PM',  '4:00 PM',
]

export function generateIntraday(seed: number): IntradayPoint[] {
  let price = seed
  return MARKET_TIMES.map(time => {
    const p = parseFloat(price.toFixed(2))
    // small random walk: ±0.5% per step, slight upward bias (52%)
    price += (Math.random() < 0.52 ? 1 : -1) * price * (0.001 + Math.random() * 0.004)
    price = Math.max(price, 0.01)
    return { time, price: p }
  })
}

// ── Chart layout constants ────────────────────────────────────────────────────

const ML = 4     // margin left
const MR = 46    // margin right (y-axis labels)
const MT = 8     // margin top
const H_CHART = 110  // chart area height (px)
const H_XAXIS = 20   // x-axis label area height (px)
const SVG_H = H_CHART + H_XAXIS

/** Compute up to maxLabels evenly-spaced indices across actual data length. */
function xLabelIndices(dataLen: number, maxLabels = 7): number[] {
  if (dataLen < 2) return [0]
  const count = Math.min(maxLabels, dataLen)
  if (count === 1) return [0]
  return Array.from({ length: count }, (_, k) =>
    Math.round(k * (dataLen - 1) / (count - 1))
  )
}

// ── Hover state ───────────────────────────────────────────────────────────────

interface HoverState {
  x:     number
  y:     number
  point: IntradayPoint
}

// ── Intraday SVG chart ────────────────────────────────────────────────────────

export const IntradayChart: React.FC<{ data: IntradayPoint[]; symbol: string; positive?: boolean }> = ({ data, symbol, positive }) => {
  const containerRef = useRef<HTMLDivElement>(null)
  const [width, setWidth]   = useState(320)
  const [hover, setHover]   = useState<HoverState | null>(null)

  // Measure container width; update on resize
  useEffect(() => {
    const el = containerRef.current
    if (!el) return
    setWidth(el.offsetWidth)
    const ro = new ResizeObserver(([entry]) => setWidth(entry.contentRect.width))
    ro.observe(el)
    return () => ro.disconnect()
  }, [])

  if (data.length < 2) return null

  const chartW = Math.max(width - ML - MR, 1)
  const chartH = H_CHART - MT

  const prices    = data.map(d => d.price)
  const minP      = Math.min(...prices)
  const maxP      = Math.max(...prices)
  const priceRange = maxP - minP || 1

  const xScale = (i: number) =>
    ML + (i / (data.length - 1)) * chartW

  const yScale = (p: number) =>
    MT + (1 - (p - minP) / priceRange) * chartH

  // positive prop overrides internal direction; when omitted defaults to green
  const isPositive = positive !== undefined ? positive : (prices[prices.length - 1] >= prices[0])
  const lineColor  = isPositive ? '#10b981' : '#ef4444'

  // Polyline & fill polygon
  const pts     = data.map((d, i) => `${xScale(i).toFixed(1)},${yScale(d.price).toFixed(1)}`).join(' ')
  const fillPts = `${xScale(0).toFixed(1)},${H_CHART} ${pts} ${xScale(data.length - 1).toFixed(1)},${H_CHART}`

  // Y-axis: min, mid, max
  const mid    = (minP + maxP) / 2
  const yTicks = [maxP, mid, minP]

  const gradId = `sc-grad-${symbol}`
  const clipId = `sc-clip-${symbol}`

  const handleMouseMove = (e: React.MouseEvent<SVGSVGElement>) => {
    const rect = e.currentTarget.getBoundingClientRect()
    const mouseX = e.clientX - rect.left
    const idx = Math.min(
      data.length - 1,
      Math.max(0, Math.round((mouseX - ML) / chartW * (data.length - 1)))
    )
    setHover({ x: xScale(idx), y: yScale(data[idx].price), point: data[idx] })
  }

  // Clamp tooltip so it stays inside the card
  const tooltipLeft = hover
    ? Math.min(hover.x + 10, width - 88)
    : 0
  const tooltipTop = hover
    ? Math.max(hover.y - 32, MT)
    : 0

  return (
    <div ref={containerRef} style={{ position: 'relative', userSelect: 'none' }}>
      <svg
        width={width}
        height={SVG_H}
        style={{ display: 'block', overflow: 'visible', cursor: 'crosshair' }}
        onMouseMove={handleMouseMove}
        onMouseLeave={() => setHover(null)}
      >
        <defs>
          <linearGradient id={gradId} x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%"   stopColor={lineColor} stopOpacity="0.22" />
            <stop offset="100%" stopColor={lineColor} stopOpacity="0"    />
          </linearGradient>
          <clipPath id={clipId}>
            <rect x={ML} y={MT} width={chartW} height={chartH} />
          </clipPath>
        </defs>

        {/* Gradient fill under the line */}
        <polygon
          points={fillPts}
          fill={`url(#${gradId})`}
          clipPath={`url(#${clipId})`}
        />

        {/* Price line */}
        <polyline
          points={pts}
          fill="none"
          stroke={lineColor}
          strokeWidth={1.5}
          strokeLinejoin="round"
          strokeLinecap="round"
          clipPath={`url(#${clipId})`}
        />

        {/* Y-axis: 3 ticks on the right, no gridlines */}
        {yTicks.map((tick, i) => (
          <text
            key={i}
            x={width - MR + 7}
            y={yScale(tick)}
            fill="#94a3b8"
            fontSize={9}
            dominantBaseline="middle"
            fontFamily="'Menlo', 'Consolas', monospace"
          >
            {tick.toFixed(2)}
          </text>
        ))}

        {/* X-axis: up to 7 evenly-spaced labels, adaptive to actual data length */}
        {xLabelIndices(data.length).map(i => (
          <text
            key={i}
            x={xScale(i)}
            y={H_CHART + 14}
            fill="#94a3b8"
            fontSize={9}
            textAnchor="middle"
            fontFamily="'Inter', system-ui, sans-serif"
          >
            {data[i].time.replace(/ [AP]M$/, '')}
          </text>
        ))}

        {/* Crosshair vertical line */}
        {hover && (
          <line
            x1={hover.x} y1={MT}
            x2={hover.x} y2={H_CHART}
            stroke="#94a3b8"
            strokeWidth={1}
            strokeDasharray="3,3"
          />
        )}

        {/* Crosshair dot on the line */}
        {hover && (
          <circle
            cx={hover.x}
            cy={hover.y}
            r={3}
            fill={lineColor}
            stroke="#fff"
            strokeWidth={1.5}
          />
        )}
      </svg>

      {/* Floating price/time tooltip */}
      {hover && (
        <div
          style={{
            position: 'absolute',
            left: tooltipLeft,
            top: tooltipTop,
            background: '#1e293b',
            color: '#f8fafc',
            padding: '4px 8px',
            borderRadius: '4px',
            fontSize: '11px',
            fontWeight: 600,
            pointerEvents: 'none',
            whiteSpace: 'nowrap',
            boxShadow: '0 2px 8px rgba(0,0,0,0.25)',
            zIndex: 10,
          }}
        >
          <div style={{ color: lineColor }}>${hover.point.price.toFixed(2)}</div>
          <div style={{ color: '#94a3b8', fontWeight: 400, fontSize: '10px' }}>{hover.point.time}</div>
        </div>
      )}
    </div>
  )
}

// ── StockCard ─────────────────────────────────────────────────────────────────

const StockCard: React.FC<StockCardProps> = ({
  symbol,
  lastPrice,
  change,
  changePct,
  data,
}) => {
  const chartData = data ?? generateIntraday(lastPrice)
  const isPositive = change >= 0
  const changeColor = isPositive ? '#10b981' : '#ef4444'
  const changeBg    = isPositive ? 'rgba(16,185,129,0.08)' : 'rgba(239,68,68,0.08)'
  const arrow       = isPositive ? '▲' : '▼'

  return (
    <div
      style={{
        background: '#fff',
        borderRadius: '12px',
        boxShadow: '0 1px 3px rgba(15,23,42,0.06), 0 1px 2px rgba(15,23,42,0.04)',
        overflow: 'hidden',
        fontFamily: "'Inter', system-ui, sans-serif",
      }}
    >
      {/* Card header */}
      <div
        style={{
          padding: '14px 16px 10px',
          display: 'flex',
          alignItems: 'flex-start',
          justifyContent: 'space-between',
        }}
      >
        <div>
          <div
            style={{
              fontSize: '14px',
              fontWeight: 700,
              color: '#0f172a',
              letterSpacing: '-0.01em',
            }}
          >
            {symbol}
          </div>
          <div
            style={{
              fontSize: '22px',
              fontWeight: 800,
              color: '#0f172a',
              letterSpacing: '-0.02em',
              fontVariantNumeric: 'tabular-nums',
              marginTop: '2px',
            }}
          >
            ${lastPrice.toFixed(2)}
          </div>
        </div>

        {/* Change badge */}
        <span
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: '3px',
            padding: '4px 8px',
            borderRadius: '6px',
            fontSize: '12px',
            fontWeight: 600,
            color: changeColor,
            background: changeBg,
          }}
        >
          {arrow}&nbsp;
          {isPositive ? '+' : ''}{change.toFixed(2)}&nbsp;
          ({isPositive ? '+' : ''}{changePct.toFixed(2)}%)
        </span>
      </div>

      {/* Chart */}
      <div style={{ padding: '0 6px 8px' }}>
        <IntradayChart data={chartData} symbol={symbol} />
      </div>
    </div>
  )
}

export default StockCard
