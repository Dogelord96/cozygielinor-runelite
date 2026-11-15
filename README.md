# CozyGielinor for RuneLite

CozyGielinor adds a clan sidebar with the active character, clan rank, clan XP
and links to the CozyGielinor portal.

This is the development source for the plugin. The portal snapshot, Discord
relay and account sync currently use local bridges. The Plugin Hub release
requires a hosted service and browser-based account pairing.

## Build

Run `./gradlew build` on macOS or Linux, or `.\gradlew.bat build` on Windows.
The `run` task launches a development client with a dedicated RuneLite profile.
The client targets Java 11 and uses RuneLite's shared OkHttp and Gson instances.

## Data used by the development plugin

| Feature | Data sent through the configured local bridge |
| --- | --- |
| Portal sidebar | Active character name |
| Clan and leadership relay | Chat messages, sender names and roster snapshots |
| Loot relay | Character, loot source, item summary, estimated value and a JPEG of the game window |
| Account progress | Character, quest points, diary tiers, combat achievement points and music unlock flags |

The local development setup enables these features with bridge configuration.
Loot capture also requires a configured loot channel and a minimum value.
Discord channel IDs, bot tokens, portal cookies and infrastructure credentials
belong in private configuration. The public plugin source contains no values
for those settings.

See [Plugin Hub submission](SUBMISSION.md) for the work required before review.
