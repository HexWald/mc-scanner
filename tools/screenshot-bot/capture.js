const fs = require('fs')
const net = require('net')
const path = require('path')
const mineflayer = require('mineflayer')
const { mineflayer: mineflayerViewer } = require('prismarine-viewer')
const { chromium } = require('playwright-core')

const DEFAULT_EDGE_PATHS = [
  'C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe',
  'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe',
  'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
  'C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe'
]

function parseArgs (argv) {
  const args = {}
  for (let i = 2; i < argv.length; i++) {
    const arg = argv[i]
    if (!arg.startsWith('--')) continue
    const key = arg.slice(2)
    const next = argv[i + 1]
    if (next == null || next.startsWith('--')) {
      args[key] = true
    } else {
      args[key] = next
      i++
    }
  }
  return args
}

function usage () {
  console.log([
    'Usage:',
    '  node capture.js --host <host> --port <port> --username <name> --out <file.png>',
    '',
    'Options:',
    '  --wait-ms <ms>       Time to wait after spawn before screenshot (default: 3000)',
    '  --timeout-ms <ms>    Whole capture timeout (default: 15000)',
    '  --version <version>  Minecraft version to use when joining (example: 1.20.4)',
    '  --width <px>         Screenshot width (default: 1280)',
    '  --height <px>        Screenshot height (default: 720)',
    '  --view-distance <n>  Viewer chunk radius (default: 4)',
    '  --edge <path>        Edge/Chrome executable path'
  ].join('\n'))
}

function findBrowserExecutable (explicitPath) {
  if (explicitPath && fs.existsSync(explicitPath)) {
    return explicitPath
  }
  for (const candidate of DEFAULT_EDGE_PATHS) {
    if (fs.existsSync(candidate)) {
      return candidate
    }
  }
  return null
}

function findFreePort () {
  return new Promise((resolve, reject) => {
    const server = net.createServer()
    server.on('error', reject)
    server.listen(0, '127.0.0.1', () => {
      const address = server.address()
      server.close(() => resolve(address.port))
    })
  })
}

function waitForEvent (emitter, eventName, timeoutMs) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      cleanup()
      reject(new Error(`Timed out waiting for ${eventName}`))
    }, timeoutMs)

    function cleanup () {
      clearTimeout(timer)
      emitter.removeListener(eventName, onEvent)
      emitter.removeListener('error', onError)
      emitter.removeListener('end', onEnd)
      emitter.removeListener('kicked', onKicked)
    }

    function onEvent (...args) {
      cleanup()
      resolve(args)
    }

    function onError (err) {
      cleanup()
      reject(err)
    }

    function onEnd (reason) {
      cleanup()
      reject(new Error(`Bot disconnected before ${eventName}: ${reason || 'unknown reason'}`))
    }

    function onKicked (reason) {
      cleanup()
      reject(new Error(`Bot kicked before ${eventName}: ${formatReason(reason)}`))
    }

    emitter.once(eventName, onEvent)
    emitter.once('error', onError)
    emitter.once('end', onEnd)
    emitter.once('kicked', onKicked)
  })
}

function formatReason (reason) {
  try {
    return JSON.stringify(reason)
  } catch (_) {
    return String(reason)
  }
}

function delay (ms) {
  return new Promise(resolve => setTimeout(resolve, ms))
}

function removePartialFile (outPath) {
  if (!outPath) return
  try {
    if (fs.existsSync(outPath)) fs.unlinkSync(outPath)
  } catch (_) {}
}

function finishWithError (outPath, errorMessage, exitCode) {
  removePartialFile(outPath)
  const response = {
    ok: false,
    error: errorMessage
  }
  console.error(JSON.stringify(response))
  process.exit(exitCode)
}

async function main () {
  const args = parseArgs(process.argv)
  if (args.help) {
    usage()
    return
  }

  const host = args.host
  const port = Number(args.port || 25565)
  const username = args.username || 'MCScanner'
  const outPath = args.out
  const waitMs = Number(args['wait-ms'] || 3000)
  const timeoutMs = Number(args['timeout-ms'] || 15000)
  const requestedVersion = args.version ? String(args.version).trim() : ''
  const width = Number(args.width || 1280)
  const height = Number(args.height || 720)
  const viewDistance = Number(args['view-distance'] || 4)
  const browserPath = findBrowserExecutable(args.edge)

  if (!host || !outPath) {
    usage()
    process.exitCode = 2
    return
  }

  fs.mkdirSync(path.dirname(path.resolve(outPath)), { recursive: true })

  if (!browserPath) {
    throw new Error('Edge or Chrome executable was not found')
  }

  let browser = null
  let bot = null
  let viewerStarted = false

  const hardTimeout = setTimeout(() => {
    const errorMessage = `Capture timed out after ${timeoutMs}ms`
    try { if (bot) bot.end() } catch (_) {}
    try { if (browser) browser.close() } catch (_) {}
    finishWithError(outPath, errorMessage, 124)
  }, timeoutMs)

  try {
    const botOptions = {
      host,
      port,
      username,
      auth: 'offline',
      hideErrors: true
    }
    if (requestedVersion) {
      botOptions.version = requestedVersion
    }
    bot = mineflayer.createBot(botOptions)

    const spawnTimeoutMs = Math.max(1000, Math.min(timeoutMs - 5000, 10000))
    await waitForEvent(bot, 'spawn', spawnTimeoutMs)

    const viewerPort = await findFreePort()
    mineflayerViewer(bot, {
      firstPerson: true,
      port: viewerPort,
      viewDistance
    })
    viewerStarted = true

    browser = await chromium.launch({
      executablePath: browserPath,
      headless: true,
      args: [
        '--enable-webgl',
        '--ignore-gpu-blocklist',
        '--no-first-run'
      ]
    })

    const page = await browser.newPage({ viewport: { width, height } })
    await page.goto(`http://127.0.0.1:${viewerPort}`, { waitUntil: 'domcontentloaded', timeout: 15000 })
    await page.waitForSelector('canvas', { timeout: 15000 })
    await delay(waitMs)
    await page.screenshot({ path: outPath, fullPage: false })

    console.log(JSON.stringify({
      ok: true,
      screenshot: path.resolve(outPath),
      version: bot.version,
      protocolVersion: bot.protocolVersion
    }))
  } finally {
    clearTimeout(hardTimeout)
    if (browser) {
      await browser.close().catch(() => {})
    }
    if (bot) {
      if (viewerStarted && bot.viewer && bot.viewer.close) {
        try { bot.viewer.close() } catch (_) {}
      }
      bot.end()
    }
  }
}

main().catch(err => {
  const args = parseArgs(process.argv)
  finishWithError(args.out, err.message || String(err), 1)
})
