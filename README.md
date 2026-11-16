# Tabby RPC

Shows what you are doing in Minecraft as your Discord Rich Presence.

Tabby RPC tells Discord where you are in the game, from the main menu to your singleplayer world or the server you are playing on. It is **client-side only**, talks directly to the Discord app on your computer and **does not change anything about the game itself**. Features include:

- Shows "Playing Minecraft" with the time played, your skin's head and your name.
- Shows the current menu, such as the main menu, the options, the server list or the creation of a new world.
- Shows the name of your singleplayer world, or the number of players on the server.
- Server addresses and icons stay hidden unless you turn them on, for all servers or for single ones when adding or editing them.
- Others can ask to join your world through Discord while it is shared with [e4mc](https://modrinth.com/mod/e4mc), and you can accept or deny right in the chat.
- Reconnects on its own when Discord is started or restarted while you play.

Everything can be changed in the settings of TabbyLib, including hiding the world name or turning off join requests. Modpacks can set their own texts, images, buttons and Discord application in the `config/custom_rpc` folder, which can be put together with the [Tabby RPC Builder](https://custom-rpc.avie29.me/).

Player heads are loaded from mc-heads.net and server icons from mcstatus.io by Discord, not by the game.

Bugs and crashes can be reported in the [issue tracker](https://github.com/avie293/avie-issues/issues) by selecting **Tabby RPC** as the project.
