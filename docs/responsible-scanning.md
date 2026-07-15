# Scanning rules

MC Scanner is for servers you own, manage, or were asked to check.

## Defaults

- Start with `MEDIUM` or `FAST`.
- Do not point it at random huge public ranges.
- Keep `DANGEROUS` for local or lab tests.
- Stop the scan if the network starts dropping packets.
- Do not publish private server lists.

## Before a long scan

- Check that the target range is correct.
- Make sure `results/` and `screenshots/` have enough disk space.
- Use a harmless nickname for whitelist checks, not your main one.
- Turn screenshots off when you only need version, ping, MOTD, and whitelist info.

## After a scan

- Review exported files before sharing them.
- Delete old screenshot folders when they stop being useful.
- Write down why the scan was run and what range was used.

Good scans are boring: clear target, sane speed, clean output.
