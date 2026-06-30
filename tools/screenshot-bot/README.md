# Screenshot bot

Local helper used by the experimental GUI screenshot mode.

It joins a Minecraft server with `mineflayer`, starts `prismarine-viewer` on a local port,
opens the viewer in headless Edge/Chrome through `playwright-core`, and saves a PNG.

## Direct test

```powershell
& "C:\Users\Raisw\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe" `
  tools\screenshot-bot\capture.js `
  --host 127.0.0.1 `
  --port 25565 `
  --username MCScanner `
  --out screenshots\test.png
```

## Notes

- Works best with offline-mode servers or servers that allow unauthenticated bots.
- Online-mode servers may kick the bot before a screenshot can be taken.
- Edge or Chrome must be installed locally.
- The Java GUI writes screenshots to `screenshots/`.
