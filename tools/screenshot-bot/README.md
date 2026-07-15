# Screenshot bot

Local helper used by the experimental GUI screenshot mode.

It joins a Minecraft server with `mineflayer`, starts `prismarine-viewer` on a local port,
opens the viewer in headless Edge/Chrome through `playwright-core`, and saves a PNG.

## Direct test

```powershell
node `
  tools\screenshot-bot\capture.js `
  --host 127.0.0.1 `
  --port 25565 `
  --username MCScanner `
  --out screenshots\test.png
```

If Node.js is not in `PATH`, point the app to it before starting the scanner:

```powershell
$env:MC_SCANNER_NODE="C:\Path\To\node.exe"
java -jar MCScanner.jar
```

## Notes

- Works best with offline-mode servers or servers that allow unauthenticated bots.
- Online-mode servers may kick the bot before a screenshot can be taken.
- Edge or Chrome must be installed locally.
- The Java GUI writes screenshots to `screenshots/`.
