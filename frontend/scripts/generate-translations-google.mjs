// Fast, free alternative to generate-translations.mjs, using Google Translate's unofficial
// Chrome-extension endpoint (no API key). Unofficial: Google may rate-limit or block it at any
// time - if that happens, re-run later or fall back to the Gemini script. Quality for short UI
// labels is a bit below the Gemini script, which sees the whole file as context.
//
// Run manually: node scripts/generate-translations-google.mjs [--fill] [--only=hi,ta] [--ns=admin] [--concurrency=3]
// Same inputs/outputs as generate-translations.mjs: reads public/locales/en/{ns}.json, writes
// public/locales/{code}/{ns}.json, skips files that already exist (so re-runs resume).
// --fill also updates existing files: keys missing from them are translated, keys no longer in
// English are dropped, and existing translations are kept untouched. (An English string whose
// wording changed under the same key keeps its old translation - rename the key, or delete that
// locale file, to force a retranslation.)
//
// {{placeholders}}, <Trans> tags, and a few never-translate terms are swapped for [[n]] tokens
// before sending and restored afterwards. A string whose tokens don't survive is retried alone,
// then kept in English rather than shipped broken.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const FRONTEND_ROOT = path.resolve(__dirname, '..')
const LOCALES_DIR = path.join(FRONTEND_ROOT, 'public', 'locales')
const SOURCE_DIR = path.join(LOCALES_DIR, 'en')
const ENDPOINT = 'https://clients5.google.com/translate_a/t'
const MAX_BATCH_CHARS = 1800
const REQUEST_DELAY_MS = 250

// Our language codes (src/i18n/languages.js) where Google uses a different one.
const GOOGLE_CODES = { zh: 'zh-CN', he: 'iw', mni: 'mni-Mtei', jv: 'jw' }

const PROTECTED = /\{\{\s*\w+\s*\}\}|<\/?\w+\s*\/?>|HydroLedger|Razorpay|\bkL\b|\bCSV\b|\bPDF\b|\bUPI\b/g

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

// Some languages (e.g. Santali) come back with the token digits in native numerals - map every
// Unicode decimal digit to its value. Nd characters always come in runs of ten, 0-9.
const DIGIT_VALUES = new Map()
for (let cp = 0, run = -1; cp <= 0x1ffff; cp += 1) {
  const ch = String.fromCodePoint(cp)
  if (/\p{Nd}/u.test(ch)) {
    run = run === -1 || run === 9 ? 0 : run + 1
    DIGIT_VALUES.set(ch, run)
  } else {
    run = -1
  }
}
const toNumber = (digits) => Number([...digits].map((d) => DIGIT_VALUES.get(d) ?? '').join(''))

function mask(text) {
  const tokens = []
  const masked = text.replace(PROTECTED, (match) => {
    tokens.push(match)
    return `[[${tokens.length - 1}]]`
  })
  return { masked, tokens }
}

// Restores tokens; returns null if any token went missing, duplicated, or appeared from nowhere.
function unmask(translated, tokens) {
  const seen = new Set()
  let ok = true
  const restored = translated.replace(/\[\[\s*(\p{Nd}+)\s*\]\]/gu, (_, digits) => {
    const index = toNumber(digits)
    if (index >= tokens.length || seen.has(index)) ok = false
    seen.add(index)
    return tokens[index] ?? ''
  })
  return ok && seen.size === tokens.length ? restored : null
}

async function translateBatch(texts, googleCode) {
  const params = new URLSearchParams({ client: 'dict-chrome-ex', sl: 'en', tl: googleCode })
  for (const text of texts) params.append('q', text)
  for (let attempt = 1; ; attempt += 1) {
    let response
    try {
      response = await fetch(`${ENDPOINT}?${params}`)
    } catch (err) {
      // Dropped connection / DNS blip - retry like a 5xx rather than failing the whole file.
      if (attempt >= 6) throw err
      console.warn(`  [network] ${googleCode}: ${err.message}, retrying in ${5 * attempt}s`)
      await sleep(5000 * attempt)
      continue
    }
    if (response.ok) {
      const data = await response.json()
      // A single q comes back as a plain array of [text, ...] pairs or strings; normalize.
      const items = typeof data === 'string' ? [data] : texts.length === 1 && !Array.isArray(data[0]) ? [data[0]] : data
      return items.map((item) => (Array.isArray(item) ? item[0] : item))
    }
    if (response.status === 400) {
      const err = new Error(`language not supported by Google (${googleCode})`)
      err.unsupported = true
      throw err
    }
    if (attempt >= 6) throw new Error(`HTTP ${response.status} after ${attempt} attempts`)
    const waitMs = 5000 * attempt
    console.warn(`  [${response.status}] ${googleCode}: backing off ${waitMs / 1000}s`)
    await sleep(waitMs)
  }
}

function collectStrings(node, out = []) {
  if (typeof node === 'string') out.push(node)
  else if (Array.isArray(node)) node.forEach((child) => collectStrings(child, out))
  else if (node && typeof node === 'object') Object.values(node).forEach((child) => collectStrings(child, out))
  return out
}

// Leaf paths in the same order collectStrings visits them, so both can be zipped by index.
function collectPaths(node, prefix = '', out = []) {
  if (typeof node === 'string') out.push(prefix)
  else if (Array.isArray(node)) node.forEach((child, i) => collectPaths(child, `${prefix}[${i}]`, out))
  else if (node && typeof node === 'object') Object.entries(node).forEach(([k, v]) => collectPaths(v, prefix ? `${prefix}.${k}` : k, out))
  return out
}

function rebuild(node, translations, cursor = { i: 0 }) {
  if (typeof node === 'string') return translations[cursor.i++]
  if (Array.isArray(node)) return node.map((child) => rebuild(child, translations, cursor))
  if (node && typeof node === 'object') {
    return Object.fromEntries(Object.entries(node).map(([k, v]) => [k, rebuild(v, translations, cursor)]))
  }
  return node
}

// `existingJson` (--fill): a previous translation of this file; its still-valid strings are reused.
async function translateFile(sourceJson, googleCode, report, existingJson = null) {
  const sources = collectStrings(sourceJson)
  const masked = sources.map(mask)
  const results = new Array(sources.length)
  const reused = new Array(sources.length).fill(null)
  if (existingJson) {
    const previous = new Map()
    const previousStrings = collectStrings(existingJson)
    collectPaths(existingJson).forEach((p, i) => previous.set(p, previousStrings[i]))
    collectPaths(sourceJson).forEach((p, i) => {
      const old = previous.get(p)
      // Only reuse when it still carries exactly the English string's placeholders/tags.
      if (typeof old === 'string' && mask(old).tokens.sort().join() === [...masked[i].tokens].sort().join()) {
        reused[i] = old
      }
    })
    report.reused = reused.filter((r) => r !== null).length
  }

  // Batch by size; strings with no letters at all (e.g. "—", "↑") are passed through as-is.
  let batch = []
  let batchChars = 0
  const flush = async () => {
    if (batch.length === 0) return
    const translated = await translateBatch(batch.map((i) => masked[i].masked), googleCode)
    batch.forEach((i, j) => { results[i] = translated[j] })
    batch = []
    batchChars = 0
    await sleep(REQUEST_DELAY_MS)
  }
  for (let i = 0; i < sources.length; i += 1) {
    if (reused[i] !== null) continue
    if (!/\p{L}/u.test(masked[i].masked.replace(/\[\[\d+\]\]/g, ''))) {
      results[i] = masked[i].masked
      continue
    }
    if (batchChars + masked[i].masked.length > MAX_BATCH_CHARS) await flush()
    batch.push(i)
    batchChars += masked[i].masked.length
  }
  await flush()

  const final = []
  for (let i = 0; i < sources.length; i += 1) {
    if (reused[i] !== null) {
      final.push(reused[i])
      continue
    }
    let restored = results[i] == null ? null : unmask(results[i], masked[i].tokens)
    if (restored === null) {
      // Batched context sometimes reshuffles tokens - one retry on its own before giving up.
      const [retry] = await translateBatch([masked[i].masked], googleCode)
      restored = unmask(retry, masked[i].tokens)
      await sleep(REQUEST_DELAY_MS)
    }
    if (restored === null) {
      report.keptEnglish += 1
      restored = sources[i]
    }
    final.push(restored)
  }
  return rebuild(sourceJson, final)
}

async function main() {
  const arg = (name) => process.argv.find((a) => a.startsWith(`--${name}=`))?.split('=')[1]
  const onlyCodes = arg('only')?.split(',')
  const onlyNs = arg('ns')?.split(',')
  const concurrency = Number(arg('concurrency') || 3)
  const fill = process.argv.includes('--fill')

  const { LANGUAGES } = await import(pathToFileURL(path.join(FRONTEND_ROOT, 'src', 'i18n', 'languages.js')))
  const namespaces = fs.readdirSync(SOURCE_DIR)
    .filter((f) => f.endsWith('.json'))
    .map((f) => f.replace(/\.json$/, ''))
    .filter((ns) => !onlyNs || onlyNs.includes(ns))
  const sources = Object.fromEntries(
    namespaces.map((ns) => [ns, JSON.parse(fs.readFileSync(path.join(SOURCE_DIR, `${ns}.json`), 'utf-8'))]),
  )

  const jobs = []
  for (const language of LANGUAGES) {
    if (language.code === 'en' || (onlyCodes && !onlyCodes.includes(language.code))) continue
    const pending = namespaces.filter((ns) => fill || !fs.existsSync(path.join(LOCALES_DIR, language.code, `${ns}.json`)))
    if (pending.length > 0) jobs.push({ language, pending })
  }
  console.log(`${jobs.reduce((n, j) => n + j.pending.length, 0)} file(s) to generate across ${jobs.length} language(s)`)

  const unsupported = []
  const failed = []
  let next = 0
  async function worker() {
    while (next < jobs.length) {
      const { language, pending } = jobs[next]
      next += 1
      const googleCode = GOOGLE_CODES[language.code] || language.code
      for (const ns of pending) {
        const report = { keptEnglish: 0, reused: 0 }
        const outFile = path.join(LOCALES_DIR, language.code, `${ns}.json`)
        try {
          const existing = fs.existsSync(outFile) ? JSON.parse(fs.readFileSync(outFile, 'utf-8')) : null
          if (existing && collectStrings(sources[ns]).length === 0) continue
          const translated = await translateFile(sources[ns], googleCode, report, fill ? existing : null)
          if (existing && JSON.stringify(existing) === JSON.stringify(translated)) continue
          fs.mkdirSync(path.dirname(outFile), { recursive: true })
          fs.writeFileSync(outFile, JSON.stringify(translated, null, 2) + '\n', 'utf-8')
          const notes = []
          if (report.reused) notes.push(`${report.reused} existing kept`)
          if (report.keptEnglish) notes.push(`${report.keptEnglish} string(s) kept in English`)
          const note = notes.length ? ` (${notes.join(', ')})` : ''
          console.log(`[done] ${language.code}/${ns}${note}`)
        } catch (err) {
          if (err.unsupported) {
            unsupported.push(language.code)
            console.log(`[skip] ${language.code}: ${err.message} - stays on the English fallback`)
            break
          }
          failed.push(`${language.code}/${ns}`)
          console.error(`[failed] ${language.code}/${ns}: ${err.message}`)
        }
      }
    }
  }
  await Promise.all(Array.from({ length: concurrency }, worker))

  if (unsupported.length) console.log(`\nNot supported by Google (English fallback): ${unsupported.join(', ')}`)
  if (failed.length) {
    console.log(`Failed (re-run to retry): ${failed.join(', ')}`)
    process.exitCode = 1
  } else {
    console.log('\nDone.')
  }
}

main().catch((err) => {
  console.error(err)
  process.exit(1)
})
