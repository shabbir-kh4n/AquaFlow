// One-time translation generator for the app's i18n namespaces.
// Run manually: node scripts/generate-translations.mjs [--only=hi,ta,es] [--ns=admin,common] [--concurrency=3]
// For every namespace file in public/locales/en/ (hand-written source of truth), and every
// target language in src/i18n/languages.js, asks Gemini to translate it and writes
// public/locales/{code}/{ns}.json. Large namespaces are split into chunks of top-level keys
// so each response stays small enough to come back intact. Skips files that already exist,
// so re-runs only fill in what's missing - delete a locale file and re-run to regenerate it.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const FRONTEND_ROOT = path.resolve(__dirname, '..')
const REPO_ROOT = path.resolve(FRONTEND_ROOT, '..')
const LOCALES_DIR = path.join(FRONTEND_ROOT, 'public', 'locales')
const SOURCE_DIR = path.join(LOCALES_DIR, 'en')
const GEMINI_MODEL = 'gemini-3.1-flash-lite'
const GEMINI_BASE_URL = 'https://generativelanguage.googleapis.com'
const MAX_CHUNK_CHARS = 5000
const MAX_ATTEMPTS = 4

function readApiKey() {
  if (process.env.GEMINI_API_KEY) {
    return process.env.GEMINI_API_KEY
  }
  const propsPath = path.join(REPO_ROOT, 'backend', 'src', 'main', 'resources', 'application-local.properties')
  const content = fs.readFileSync(propsPath, 'utf-8')
  const match = content.match(/^gemini\.api\.key=(.+)$/m)
  if (!match || !match[1].trim()) {
    throw new Error('gemini.api.key not found in application-local.properties and GEMINI_API_KEY env var not set')
  }
  return match[1].trim()
}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

// Splits an object into several objects of whole top-level keys, each serializing to at most
// roughly MAX_CHUNK_CHARS. A single oversized top-level key is split one level deeper.
function chunkObject(source) {
  const chunks = []
  let current = {}
  let currentSize = 0
  for (const [key, value] of Object.entries(source)) {
    const size = JSON.stringify(value).length
    if (size > MAX_CHUNK_CHARS && value && typeof value === 'object' && !Array.isArray(value)) {
      if (currentSize > 0) {
        chunks.push(current)
        current = {}
        currentSize = 0
      }
      for (const sub of chunkObject(value)) {
        chunks.push({ [key]: sub })
      }
      continue
    }
    if (currentSize > 0 && currentSize + size > MAX_CHUNK_CHARS) {
      chunks.push(current)
      current = {}
      currentSize = 0
    }
    current[key] = value
    currentSize += size
  }
  if (currentSize > 0) chunks.push(current)
  return chunks
}

function deepMerge(target, source) {
  for (const [key, value] of Object.entries(source)) {
    if (value && typeof value === 'object' && !Array.isArray(value) && target[key] && typeof target[key] === 'object') {
      deepMerge(target[key], value)
    } else {
      target[key] = value
    }
  }
  return target
}

// Same keys/array lengths at every level, and every {{placeholder}} kept verbatim.
function sameShape(source, translated, where = '') {
  if (typeof source === 'string') {
    if (typeof translated !== 'string') return `${where}: expected string`
    const placeholders = (s) => (s.match(/\{\{\s*\w+\s*\}\}/g) || []).map((p) => p.replace(/\s/g, '')).sort().join(',')
    if (placeholders(source) !== placeholders(translated)) return `${where}: placeholders changed`
    // <Trans> markup such as <strong>...</strong> or <1>...</1> must survive too.
    const tags = (s) => (s.match(/<\/?\w+\s*\/?>/g) || []).sort().join(',')
    if (tags(source) !== tags(translated)) return `${where}: markup tags changed`
    return null
  }
  if (Array.isArray(source)) {
    if (!Array.isArray(translated) || translated.length !== source.length) return `${where}: array length changed`
    for (let i = 0; i < source.length; i += 1) {
      const err = sameShape(source[i], translated[i], `${where}[${i}]`)
      if (err) return err
    }
    return null
  }
  if (source && typeof source === 'object') {
    if (!translated || typeof translated !== 'object' || Array.isArray(translated)) return `${where}: expected object`
    for (const key of Object.keys(source)) {
      if (!(key in translated)) return `${where}.${key}: missing`
      const err = sameShape(source[key], translated[key], `${where}.${key}`)
      if (err) return err
    }
    return null
  }
  return null
}

async function callGemini(apiKey, prompt) {
  for (let attempt = 1; ; attempt += 1) {
    const response = await fetch(
      `${GEMINI_BASE_URL}/v1beta/models/${GEMINI_MODEL}:generateContent?key=${apiKey}`,
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          contents: [{ parts: [{ text: prompt }] }],
          generationConfig: { responseMimeType: 'application/json' },
        }),
      },
    )
    if (response.ok) {
      const data = await response.json()
      const parts = data?.candidates?.[0]?.content?.parts ?? []
      return parts.map((p) => p.text || '').join('').trim()
    }
    const errorText = await response.text()
    // Rate-limited or transiently unavailable - back off and retry; anything else is fatal for this chunk.
    if ((response.status === 429 || response.status >= 500) && attempt < 8) {
      if (/per ?day|PerDay/i.test(errorText)) {
        throw new Error(`Daily quota exhausted: ${errorText.slice(0, 300)}`)
      }
      const waitMs = Math.min(60000, 5000 * attempt)
      console.warn(`  [${response.status}] backing off ${waitMs / 1000}s`)
      await sleep(waitMs)
      continue
    }
    throw new Error(`Gemini API error ${response.status}: ${errorText.slice(0, 300)}`)
  }
}

async function translateChunk(apiKey, chunk, language) {
  const prompt = `You are translating the user interface of HydroLedger, a water usage monitoring and billing web app `
    + `for apartment communities. Translate all string values in this JSON into ${language.label} (${language.code}). `
    + `Use short, natural UI wording. Preserve the exact JSON structure and all keys unchanged. `
    + `Keep {{placeholders}} and markup tags like <strong>...</strong> exactly as written (translate the text between tags). Keep "HydroLedger", "kL", "₹", "CSV", "PDF" and similar codes unchanged. `
    + `Return only valid JSON.\n\n${JSON.stringify(chunk)}`

  let lastError
  for (let attempt = 1; attempt <= MAX_ATTEMPTS; attempt += 1) {
    try {
      const text = await callGemini(apiKey, prompt)
      const cleaned = text.replace(/^```(?:json)?\s*/i, '').replace(/```\s*$/, '')
      const translated = JSON.parse(cleaned)
      const shapeError = sameShape(chunk, translated)
      if (shapeError) throw new Error(`structure mismatch at ${shapeError}`)
      return translated
    } catch (err) {
      if (err.message.startsWith('Daily quota')) throw err
      lastError = err
    }
  }
  throw lastError
}

async function translateFile(apiKey, sourceJson, language) {
  const result = {}
  for (const chunk of chunkObject(sourceJson)) {
    deepMerge(result, await translateChunk(apiKey, chunk, language))
  }
  return result
}

async function main() {
  const arg = (name) => process.argv.find((a) => a.startsWith(`--${name}=`))?.split('=')[1]
  const onlyCodes = arg('only')?.split(',')
  const onlyNs = arg('ns')?.split(',')
  const concurrency = Number(arg('concurrency') || 3)

  const languagesUrl = pathToFileURL(path.join(FRONTEND_ROOT, 'src', 'i18n', 'languages.js'))
  const { LANGUAGES } = await import(languagesUrl)
  const apiKey = readApiKey()

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
    for (const ns of namespaces) {
      const outFile = path.join(LOCALES_DIR, language.code, `${ns}.json`)
      if (!fs.existsSync(outFile)) jobs.push({ language, ns, outFile })
    }
  }
  console.log(`${jobs.length} file(s) to generate across ${namespaces.join(', ')}`)

  const failed = []
  let quotaExhausted = false
  let next = 0
  async function worker() {
    while (!quotaExhausted && next < jobs.length) {
      const { language, ns, outFile } = jobs[next]
      next += 1
      try {
        console.log(`[translate] ${language.code}/${ns}`)
        const translated = await translateFile(apiKey, sources[ns], language)
        fs.mkdirSync(path.dirname(outFile), { recursive: true })
        fs.writeFileSync(outFile, JSON.stringify(translated, null, 2) + '\n', 'utf-8')
        console.log(`[done] ${language.code}/${ns}`)
      } catch (err) {
        console.error(`[failed] ${language.code}/${ns}: ${err.message}`)
        failed.push(`${language.code}/${ns}`)
        if (err.message.startsWith('Daily quota')) quotaExhausted = true
      }
    }
  }
  await Promise.all(Array.from({ length: concurrency }, worker))

  if (quotaExhausted) {
    console.log('\nStopped early: Gemini daily quota exhausted. Re-run later to continue where it left off.')
  }
  if (failed.length > 0) {
    console.log(`\nFailed (re-run this script to retry): ${failed.join(', ')}`)
    process.exitCode = 1
  } else if (!quotaExhausted) {
    console.log('\nAll requested files generated successfully.')
  }
}

main().catch((err) => {
  console.error(err)
  process.exit(1)
})
