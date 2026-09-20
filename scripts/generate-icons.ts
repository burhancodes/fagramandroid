import type { SvgShape } from './svg-to-vector.js'
import fs from 'node:fs/promises'
import { dirname, join } from 'node:path'
import { rootDir } from './config.js'
import { ensureDir, step, success } from './lib.js'
import {
  fmtNum,
  parseSvgBody,
  resolveFillColor,
  resolveStrokeColor,

} from './svg-to-vector.js'

// run manually after changing src/res/launcher SVGs; output is committed

const ADAPTIVE_SIZE = 108
const FG_SAFE = 72
// settings-list icon: 24dp render in a 72dp viewport, tinted white by
// SettingCell — mirrors stock settings_account/settings_chat/settings_privacy
// settings-list icon: 24dp render in a 72dp viewport
// SettingCell — mirrors stock settings_account/settings_chat/settings_privacy
const SETTINGS_DP = 24
const SETTINGS_VIEWPORT = 72
const SETTINGS_SAFE = 80
// notification small icon: 24dp white silhouette. safe scales the glyph
// up to ~21dp (the Material small-icon target inside a 24dp canvas).
const NOTIFICATION_DP = 24
const NOTIFICATION_VIEWPORT = 24
const NOTIFICATION_SAFE = 39

// debug badge: a small white square (only its top-left corner rounded) tucked
// into the bottom-right corner, holding a β. the white fill is framed with a
// background-coloured outline so it doesn't clash with the icon underneath.
const DEBUG_BADGE_COLOR = '#FFFFFFFF'
// β glyph from Material Design Icons (Apache-2.0), viewBox 0 0 24 24
const BETA_PATH = 'M9.23 17.59v5.53H6.88V6.72c0-1.45.43-2.59 1.28-3.44C9 2.43 10.17 2 11.61 2c1.39 0 2.46.34 3.26 1c.79.68 1.18 1.62 1.18 2.81c0 .82-.26 1.59-.78 2.3s-1.19 1.2-2.02 1.47v.04c1.25.2 2.22.65 2.88 1.38c.66.71.99 1.62.99 2.74c0 1.32-.46 2.4-1.37 3.23c-.92.83-2.12 1.24-3.62 1.24c-1.06 0-2.03-.21-2.9-.62m1.49-6.84V8.83c.87-.11 1.58-.43 2.15-.97c.56-.55.84-1.16.84-1.86c0-1.38-.71-2.08-2.11-2.08c-.76 0-1.35.24-1.76.73s-.61 1.17-.61 2.06v8.79c.91.53 1.8.79 2.66.79c.84 0 1.5-.22 1.97-.65c.47-.44.7-1.06.7-1.85c0-1.79-1.28-2.79-3.84-3.04'
const GLYPH_VIEWBOX = 24
// the β path isn't centered in its 24x24 viewBox — its ink box leans toward
// the bottom-right, so the badge centers on the box, not the viewBox
const BETA_BBOX = { x: 6.88, y: 2, width: 10.24, height: 21.12 }

// debug badge geometry, in the 108dp adaptive-icon viewport
const BADGE_SIZE = 26
const BADGE_CORNER_RADIUS = 12
// gap between the badge edge and the β glyph box
const BADGE_PADDING = 5
// bg-coloured frame; the stroke is centered on the edge, so ~half shows outside
const BADGE_OUTLINE_WIDTH = 4
// pin the badge's outer corner onto the launcher's circular icon mask (the
// inscribed circle of the viewport) so it sits flush in the bottom-right
// corner; the frame's outer tip clips against the mask, which is harmless
const MASK_RADIUS = ADAPTIVE_SIZE / 2
const BADGE_FAR = ADAPTIVE_SIZE / 2 + MASK_RADIUS / Math.SQRT2
const BADGE_NEAR = BADGE_FAR - BADGE_SIZE

// committed under src/res, synced into the worktree by forkSyncFiles
const GEN_DRAWABLE = 'src/res/launcher/generated/drawable'
const GEN_DEBUG_MIPMAP = 'src/res/launcher/generated/mipmap-debug'

function toMonochromeColor(hexColor: string, bgHex = '#FFFF5858'): string {
  const hex = hexColor.replace(/^#/, '')
  const fillR = Number.parseInt(hex.length === 8 ? hex.slice(2, 4) : hex.slice(0, 2), 16)
  const fillG = Number.parseInt(hex.length === 8 ? hex.slice(4, 6) : hex.slice(2, 4), 16)
  const fillB = Number.parseInt(hex.length === 8 ? hex.slice(6, 8) : hex.slice(4, 6), 16)

  if (fillR >= 250 && fillG >= 250 && fillB >= 250) {
    return '#FFFFFFFF'
  }

  const bgClean = bgHex.replace(/^#/, '')
  const bgR = Number.parseInt(bgClean.length === 8 ? bgClean.slice(2, 4) : bgClean.slice(0, 2), 16)
  const bgG = Number.parseInt(bgClean.length === 8 ? bgClean.slice(4, 6) : bgClean.slice(2, 4), 16)
  const bgB = Number.parseInt(bgClean.length === 8 ? bgClean.slice(6, 8) : bgClean.slice(4, 6), 16)

  const diffs = [
    { fill: fillR, bg: bgR, max: 255 - bgR },
    { fill: fillG, bg: bgG, max: 255 - bgG },
    { fill: fillB, bg: bgB, max: 255 - bgB },
  ].filter(d => d.max > 10)

  let alpha = 0.5
  if (diffs.length > 0) {
    const alphas = diffs.map(d => Math.max(0, Math.min(1, (d.fill - d.bg) / d.max)))
    alpha = alphas.reduce((a, b) => a + b, 0) / alphas.length
  } else {
    alpha = (0.2126 * fillR + 0.7152 * fillG + 0.0722 * fillB) / 255
  }

  const alphaByte = Math.max(0x20, Math.min(0xFF, Math.round(alpha * 255)))
  const alphaHex = alphaByte.toString(16).padStart(2, '0').toUpperCase()
  return `#${alphaHex}FFFFFF`
}

function shapeToPathXml(shape: SvgShape, monochrome: boolean, bgColor = '#FFFF5858'): string | null {
  const fill = resolveFillColor(shape.fill)
  const stroke = resolveStrokeColor(shape.stroke)
  if (!fill && !stroke) return null
  const attrs: string[] = [`android:pathData="${shape.d}"`]
  if (fill) {
    const finalFill = monochrome ? toMonochromeColor(fill, bgColor) : fill
    attrs.push(`android:fillColor="${finalFill}"`)
  }
  if (stroke) {
    const finalStroke = monochrome ? toMonochromeColor(stroke, bgColor) : stroke
    attrs.push(`android:strokeColor="${finalStroke}"`)
    attrs.push(`android:strokeWidth="${fmtNum(Number(shape.strokeWidth ?? 1))}"`)
    if (shape.strokeLineCap) attrs.push(`android:strokeLineCap="${shape.strokeLineCap}"`)
    if (shape.strokeLineJoin) attrs.push(`android:strokeLineJoin="${shape.strokeLineJoin}"`)
  }
  return `        <path\n            ${attrs.join('\n            ')} />`
}

function buildDebugBadge(markColor: string): string {
  const r = BADGE_CORNER_RADIUS
  // white square with only the top-left corner rounded, traced clockwise
  const badge = `M${fmtNum(BADGE_NEAR + r)},${fmtNum(BADGE_NEAR)}`
    + ` H${fmtNum(BADGE_FAR)} V${fmtNum(BADGE_FAR)} H${fmtNum(BADGE_NEAR)}`
    + ` V${fmtNum(BADGE_NEAR + r)} A${fmtNum(r)},${fmtNum(r)} 0 0 1`
    + ` ${fmtNum(BADGE_NEAR + r)},${fmtNum(BADGE_NEAR)} Z`
  // β: scale the 24x24 glyph viewBox to the padded badge interior, then center
  // the glyph's ink box (not the viewBox) on the badge
  const scale = (BADGE_SIZE - 2 * BADGE_PADDING) / GLYPH_VIEWBOX
  const badgeCenter = (BADGE_NEAR + BADGE_FAR) / 2
  const offsetX = badgeCenter - (BETA_BBOX.x + BETA_BBOX.width / 2) * scale
  const offsetY = badgeCenter - (BETA_BBOX.y + BETA_BBOX.height / 2) * scale
  return `\n    <path
        android:pathData="${badge}"
        android:fillColor="${DEBUG_BADGE_COLOR}"
        android:strokeColor="${markColor}"
        android:strokeWidth="${fmtNum(BADGE_OUTLINE_WIDTH)}" />
    <group
        android:translateX="${fmtNum(offsetX)}"
        android:translateY="${fmtNum(offsetY)}"
        android:scaleX="${fmtNum(scale)}"
        android:scaleY="${fmtNum(scale)}">
        <path
            android:pathData="${BETA_PATH}"
            android:fillColor="${markColor}" />
    </group>`
}

interface ScaledVectorOpts {
  widthDp: number
  viewport: number
  safe: number
  overlay?: string
}

// scale an SVG body to `safe` units, centered inside a `viewport`-sized canvas
function buildScaledVector(shapes: SvgShape[], srcW: number, srcH: number, monochrome: boolean, opts: ScaledVectorOpts, bgColor = '#FFFF5858'): string {
  const inset = (opts.viewport - opts.safe) / 2
  const scale = opts.safe / Math.max(srcW, srcH)
  const offsetX = inset + (opts.safe - srcW * scale) / 2
  const offsetY = inset + (opts.safe - srcH * scale) / 2
  const paths = shapes
    .map(s => shapeToPathXml(s, monochrome, bgColor))
    .filter((s): s is string => s !== null)
  return `<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="${fmtNum(opts.widthDp)}dp"
    android:height="${fmtNum(opts.widthDp)}dp"
    android:viewportWidth="${opts.viewport}"
    android:viewportHeight="${opts.viewport}">
    <group
        android:translateX="${fmtNum(offsetX)}"
        android:translateY="${fmtNum(offsetY)}"
        android:scaleX="${fmtNum(scale)}"
        android:scaleY="${fmtNum(scale)}">
${paths.join('\n')}
    </group>${opts.overlay ?? ''}
</vector>
`
}

function buildForegroundVector(shapes: SvgShape[], srcW: number, srcH: number, monochrome: boolean, debug = false, bgColor = '#FFFF5858'): string {
  return buildScaledVector(shapes, srcW, srcH, monochrome, {
    widthDp: ADAPTIVE_SIZE,
    viewport: ADAPTIVE_SIZE,
    safe: FG_SAFE,
    overlay: debug ? buildDebugBadge(bgColor) : undefined,
  }, bgColor)
}

function buildSettingsVector(shapes: SvgShape[], srcW: number, srcH: number, bgColor = '#FFFF5858'): string {
  return buildScaledVector(shapes, srcW, srcH, true, {
    widthDp: SETTINGS_DP,
    viewport: SETTINGS_VIEWPORT,
    safe: SETTINGS_SAFE,
  }, bgColor)
}

function buildNotificationVector(shapes: SvgShape[], srcW: number, srcH: number, bgColor = '#FFFF5858'): string {
  return buildScaledVector(shapes, srcW, srcH, true, {
    widthDp: NOTIFICATION_DP,
    viewport: NOTIFICATION_VIEWPORT,
    safe: NOTIFICATION_SAFE,
  }, bgColor)
}

function buildAdaptiveIcon(foreground: string): string {
  return `<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/icon_background_inu" />
    <foreground android:drawable="@drawable/${foreground}" />
    <monochrome android:drawable="@drawable/icon_plane_inu" />
</adaptive-icon>
`
}

function buildBackgroundVector(bgColor = '#FFFF5858'): string {
  return `<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="${ADAPTIVE_SIZE}dp"
    android:height="${ADAPTIVE_SIZE}dp"
    android:viewportWidth="${ADAPTIVE_SIZE}"
    android:viewportHeight="${ADAPTIVE_SIZE}">
    <path
        android:pathData="M0,0h${ADAPTIVE_SIZE}v${ADAPTIVE_SIZE}h-${ADAPTIVE_SIZE}z"
        android:fillColor="${bgColor}" />
</vector>
`
}

async function writeGenerated(relPath: string, content: string): Promise<boolean> {
  const absPath = join(rootDir, relPath)
  await ensureDir(dirname(absPath))
  const current = await fs.readFile(absPath, 'utf8').catch(() => null)
  if (current === content) return false
  step(`Generating ${relPath}`)
  await fs.writeFile(absPath, content)
  return true
}

interface LoadedSvg {
  shapes: SvgShape[]
  srcW: number
  srcH: number
  bgColor: string
}

async function loadSvg(relPath: string): Promise<LoadedSvg> {
  const svgPath = join(rootDir, relPath)
  const svg = await fs.readFile(svgPath, 'utf8')
  const viewBox = svg.match(/viewBox\s*=\s*"\s*0\s+0\s+([\d.]+)\s+([\d.]+)\s*"/)
  if (!viewBox) throw new Error(`${relPath} missing viewBox starting at 0 0`)
  const srcW = Number(viewBox[1])
  const srcH = Number(viewBox[2])
  const allShapes = parseSvgBody(svg)

  let bgColor = '#FFFF5858'
  const foregroundShapes: SvgShape[] = []

  for (const s of allShapes) {
    const isBg = (s.tag === 'rect' && Number(s.attrs?.width) >= srcW && Number(s.attrs?.height) >= srcH)
      || (s.tag === 'circle' && Number(s.attrs?.r) >= srcW / 2)
    if (isBg && s.fill) {
      const resolved = resolveFillColor(s.fill)
      if (resolved) bgColor = resolved
    } else {
      foregroundShapes.push(s)
    }
  }

  return { shapes: foregroundShapes, srcW, srcH, bgColor }
}

const fg = await loadSvg('src/res/launcher/icon.svg')

const monoPath = join(rootDir, 'src/res/launcher/icon-mono.svg')
const hasMono = await fs.access(monoPath).then(() => true, () => false)
const mono = hasMono ? await loadSvg('src/res/launcher/icon-mono.svg') : fg

const foreground = buildForegroundVector(fg.shapes, fg.srcW, fg.srcH, false, false, fg.bgColor)
const foregroundDebug = buildForegroundVector(fg.shapes, fg.srcW, fg.srcH, false, true, fg.bgColor)
const monochrome = buildForegroundVector(mono.shapes, mono.srcW, mono.srcH, true, false, fg.bgColor)
const settingsIcon = buildSettingsVector(mono.shapes, mono.srcW, mono.srcH, fg.bgColor)
const notificationIcon = buildNotificationVector(mono.shapes, mono.srcW, mono.srcH, fg.bgColor)
const background = buildBackgroundVector(fg.bgColor)
const debugIcon = buildAdaptiveIcon('icon_foreground_inu_debug')

// the *_inu drawables back stock @mipmap/ic_launcher{,_round} (rewired by
// misc__branding); the mipmap-debug wrappers override it for the debug build
const targets: [string, string][] = [
  [`${GEN_DRAWABLE}/icon_background_inu.xml`, background],
  [`${GEN_DRAWABLE}/icon_plane_inu.xml`, monochrome],
  [`${GEN_DRAWABLE}/icon_foreground_inu.xml`, foreground],
  [`${GEN_DRAWABLE}/icon_foreground_inu_round.xml`, foreground],
  [`${GEN_DRAWABLE}/icon_foreground_inu_debug.xml`, foregroundDebug],
  [`${GEN_DRAWABLE}/icon_settings_inu.xml`, settingsIcon],
  [`${GEN_DRAWABLE}/icon_notification_inu.xml`, notificationIcon],
  [`${GEN_DEBUG_MIPMAP}/ic_launcher.xml`, debugIcon],
  [`${GEN_DEBUG_MIPMAP}/ic_launcher_round.xml`, debugIcon],
]

let dirty = false
for (const [rel, content] of targets) {
  if (await writeGenerated(rel, content)) dirty = true
}

success(dirty ? 'Launcher icons generated' : 'Launcher icons already up to date')
