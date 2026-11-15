# Plugin Hub submission

## Release work

- Route bridge requests over HTTPS on the CozyGielinor domain. Store Discord
  channel IDs, bot tokens and infrastructure credentials in private server
  configuration.
- Pair the plugin to an authenticated portal account in the browser. Issue
  expiring, revocable credentials scoped to the verified character. Check
  membership and leadership permissions on the server for each private request.
- Document and add explicit controls for chat forwarding, progress uploads
  and loot screenshots. Address chat visibility in screenshots before uploads.
- Confirm that forwarding clan chat and gathering roster names is acceptable to
  RuneLite reviewers. The current implementation collects other players' names,
  which falls within an area covered by the rejected-features policy.
- Exercise installation, pairing, logout and revocation with member, guest,
  and leadership accounts. Verify chat in both directions, progress persistence
  and a real screenshot upload.
- Verify CozyManager changes using `node scripts/test-all.mjs` from the
  application root. Run mutation checks against the isolated test server.
- Confirm redistribution rights for the logo and choose the public source license.

## Submit

Fork [runelite/plugin-hub](https://github.com/runelite/plugin-hub) and add
`plugins/cozygielinor` with the public repository and full release commit SHA.

```properties
repository=https://github.com/Dogelord96/cozygielinor-runelite.git
commit=<full-release-commit-sha>
```

Open a pull request against the upstream Plugin Hub and follow the build and
review on that pull request.

[Submission instructions](https://github.com/runelite/plugin-hub#submitting-a-plugin)
· [RuneLite rejected features](https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features)
