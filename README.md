# CozyGielinor for RuneLite

A dedicated RuneLite plugin connecting the CozyGielinor clan with its clan portal and Discord community.

## Features

- **Automatic two-way clan chat:** Connects when you are logged in and have the CozyGielinor clan chat open, including as a guest. Your own messages are forwarded to the community Discord channel, and Discord replies appear locally in RuneLite. No pairing code or separate relay setting is needed.
- **In-game event broadcasts:** Shows announcements from the clan's own bot in the community relay, without echoing relayed player messages.
- **Leadership tab and Friends Chat relay:** After verifying a linked character, authorized leadership members see the next leadership meeting and a link to its agenda. The private relay connects automatically while the CozyGielinor Friends Chat is open.
- **Clan portal progress sync:** A paired character shares supported music unlocks, quest progress and achievement diary progress with the clan portal.

## Data sharing

This plugin connects to `https://home.cozygielinor.net`, a service operated separately from RuneLite. Requests reveal your IP address. While the community relay is active, your character name and your own clan chat messages are sent to the community Discord channel automatically. Installing and enabling the plugin enables this sharing; disable the plugin to stop it.

The community relay is public and its character labels are unverified. It provides no access to private leadership messages, private portal data or account progress updates. Leadership access requires a verified pairing; private Friends Chat relay messages and paired account progress are sent only for the active linked character.

The locally saved credential grants access to that paired character's portal features. Do not share it. You can revoke it using **Disconnect RuneLite sessions** on the portal's connection page.

The plugin requires the CozyGielinor online services to be available. Contact the clan team for help with pairing or access.
