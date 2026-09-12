// One-time translation generator for the landing page's i18n content.
// Run manually: node scripts/generate-translations.mjs [--only=hi,ta,es]
// Reads public/locales/en/landing.json (hand-written source of truth) and, for
// every target language in src/i18n/languages.js, asks Gemini to translate it
// once and writes public/locales/{code}/landing.json. Skips languages whose
// file already exists, so re-runs only fill in what's missing - delete a
// locale file and re-run to regenerate just that language.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const FRONTEND_ROOT = path.resolve(__dirname, '..')
const REPO_ROOT = path.resolve(FRONTEND_ROOT, '..')
const SOURCE_FILE = path.join(FRONTEND_ROOT, 'public', 'locales', 'en', 'landing.json')
const LOCALES_DIR = path.join(FRONTEND_ROOT, 'public', 'locales')
const GEMINI_MODEL = 'gemini-3.1-flash-lite'
const GEMINI_BASE_URL = 'https://generativelanguage.googleapis.com'
const REQUEST_DELAY_MS = 300

// Paths (dot-separated, array indices as numbers) whose values must never be
// sent to Gemini or altered - proper nouns / names paired with translatable text.
// (Currently empty: every non-translatable value - partner names, the office
// address, testimonial author names/roles, currency figures - was kept out of
// landing.json entirely as local component consts instead. Kept as an escape
// hatch for any future key that must ship in the JSON but stay untranslated.)
const DO_NOT_TRANSLATE_PATHS = []

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

function getAtPath(obj, dottedPath) {
  const parts = dottedPath.split('.')
  let cur = obj
  for (const part of parts) {
    if (cur == null) return undefined
    cur = cur[part]
  }
  return cur
}

function setAtPath(obj, dottedPath, value) {
  const parts = dottedPath.split('.')
  let cur = obj
  for (let i = 0; i < parts.length - 1; i += 1) {
    cur = cur[parts[i]]
    if (cur == null) return
  }
  cur[parts[parts.length - 1]] = value
}

function stripDoNotTranslate(source) {
  const payload = JSON.parse(JSON.stringify(source))
  for (const p of DO_NOT_TRANSLATE_PATHS) {
    if (getAtPath(payload, p) !== undefined) {
      setAtPath(payload, p, '')
    }
  }
  return payload
}

function restoreDoNotTranslate(translated, source) {
  for (const p of DO_NOT_TRANSLATE_PATHS) {
    const original = getAtPath(source, p)
    if (original !== undefined) {
      setAtPath(translated, p, original)
    }
  }
  return translated
}

async function translateOne(apiKey, sourceJson, language) {
  const payload = stripDoNotTranslate(sourceJson)
  const prompt = `Translate all non-empty string values in this JSON into ${language.label} (${language.code}). `
    + `Preserve the exact JSON structure and all keys unchanged. Leave any empty string values ("") empty. `
    + `Return only valid JSON, no markdown code fences, no commentary.\n\n${JSON.stringify(payload)}`

  const response = await fetch(
    `${GEMINI_BASE_URL}/v1beta/models/${GEMINI_MODEL}:generateContent?key=${apiKey}`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ contents: [{ parts: [{ text: prompt }] }] }),
    },
  )

  if (!response.ok) {
    const errorText = await response.text()
    throw new Error(`Gemini API error ${response.status}: ${errorText}`)
  }

  const data = await response.json()
  const parts = data?.candidates?.[0]?.content?.parts ?? []
  const text = parts.map((p) => p.text || '').join('').trim()
  const cleaned = text.replace(/^```(?:json)?\s*/i, '').replace(/```\s*$/, '')

  let translated
  try {
    translated = JSON.parse(cleaned)
  } catch (err) {
    throw new Error(`Failed to parse Gemini response as JSON: ${err.message}\nRaw text: ${cleaned.slice(0, 500)}`)
  }

  return restoreDoNotTranslate(translated, sourceJson)
}

async function main() {
  const onlyArg = process.argv.find((a) => a.startsWith('--only='))
  const onlyCodes = onlyArg ? onlyArg.replace('--only=', '').split(',') : null

  const languagesUrl = pathToFileURL(path.join(FRONTEND_ROOT, 'src', 'i18n', 'languages.js'))
  const { LANGUAGES } = await import(languagesUrl)
  const apiKey = readApiKey()
  const sourceJson = JSON.parse(fs.readFileSync(SOURCE_FILE, 'utf-8'))

  const targets = LANGUAGES.filter((l) => l.code !== 'en' && (!onlyCodes || onlyCodes.includes(l.code)))

  const failed = []
  for (const language of targets) {
    const outDir = path.join(LOCALES_DIR, language.code)
    const outFile = path.join(outDir, 'landing.json')

    if (fs.existsSync(outFile)) {
      console.log(`[skip] ${language.code} (${language.label}) - already generated`)
      continue
    }

    try {
      console.log(`[translate] ${language.code} (${language.label})...`)
      const translated = await translateOne(apiKey, sourceJson, language)
      fs.mkdirSync(outDir, { recursive: true })
      fs.writeFileSync(outFile, JSON.stringify(translated, null, 2) + '\n', 'utf-8')
      console.log(`[done] ${language.code}`)
    } catch (err) {
      console.error(`[failed] ${language.code}: ${err.message}`)
      failed.push(language.code)
    }

    await new Promise((resolve) => setTimeout(resolve, REQUEST_DELAY_MS))
  }

  if (failed.length > 0) {
    console.log(`\nFailed languages (re-run this script to retry): ${failed.join(', ')}`)
  } else {
    console.log('\nAll requested languages generated successfully.')
  }
}

main().catch((err) => {
  console.error(err)
  process.exit(1)
})
