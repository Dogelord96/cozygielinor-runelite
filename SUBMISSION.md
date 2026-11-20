# Plugin Hub submission

## v0.2 review notes

- The community clan relay connects automatically for logged-in characters in the CozyGielinor clan or guest clan channel. It forwards only the active character's own messages. Community character labels are explicitly unverified.
- The Plugin Hub marker includes an installation warning covering the automatic sharing of IP address, character name and clan chat messages, plus paired progress and private leadership features.
- Private portal progress and leadership access require a one-time browser-generated code. The credential is stored locally and expires after 30 days. The portal stores only a token digest, supports revocation and checks membership, linked character and leadership permissions on private requests.
- The Leadership tab shows the next meeting after authorization. Losing leadership rights revokes the previously approved connection. Regular paired members do not receive leadership data.
- The relay displays Discord messages locally. Clan announcements from the service's own bot are included; forwarded player messages are not echoed.
- `gradlew.bat build --offline` includes offline behavioral checks for saved credentials, character boundaries, dashboard controls and leadership revocation.
- Server changes must pass `node scripts/test-all.mjs` from the final application root. The runner uses an isolated workspace, database and HTTP server; never run mutation checks against production.
- Live gameplay with real member, guest and leadership characters still requires manual verification. The automated checks do not represent an in-game end-to-end test.

Update the existing Plugin Hub pull request to the public release commit. Keep the repository's BSD 2-Clause license template and installation warning.
