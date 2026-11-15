# Plugin Hub submission

## Release work

- Deploy the pairing, progress and chat endpoints on the CozyGielinor HTTPS
  portal. Configure the Discord channels in the private server environment.
- Test pairing with a linked character. The portal authorizes a one-time code
  and the client keeps the resulting expiring token in memory. The portal can
  revoke the token and checks the character link and membership on requests.
- Pairing enables progress sync. The Discord relay has a separate opt-in.
  Before including loot screenshots in the hosted release, add an explicit
  control and address chat visibility in the captured frame.
- Explain the relay direction and scope in the submission. The release client
  forwards messages written by its active character and displays Discord
  replies locally. It does not gather channel rosters or forward other players'
  game chat.
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
