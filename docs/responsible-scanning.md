# Responsible scanning notes

MC Scanner is meant for checking servers you own, manage, or have permission to test.

## Good default behavior

- Start with `MEDIUM` or `FAST`.
- Avoid large public IP ranges unless you have a clear reason and permission.
- Keep `DANGEROUS` for controlled local tests only.
- Stop the scan if the target network starts dropping packets or behaving badly.
- Do not publish server lists that contain private or sensitive targets.

## Before a long scan

- Check that the target range is correct.
- Make sure results and screenshots have enough disk space.
- Use a harmless nickname for whitelist checks.
- Keep screenshot capture disabled when you only need basic server metadata.

## After a scan

- Review exported files before sharing them.
- Delete old screenshots if they are no longer useful.
- Keep notes about why the scan was run and what target range was used.

The scanner is most useful when it is boring, predictable, and respectful to other people's infrastructure.
