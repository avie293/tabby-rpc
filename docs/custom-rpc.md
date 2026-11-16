# Custom Rich Presence for Modpacks

Modpacks can change what Tabby RPC shows in Discord by shipping a `config/custom_rpc` folder. Every file and every
value is optional, whatever is left out keeps the default. The player's head and name in the small image can not be
changed.

Changes are picked up with `/tabbyrpc reload`, so the game does not have to be restarted. Problems such as broken
files or unknown placeholders are listed in the log.

## settings.json

```json
{
  "application_id": "123456789012345678",
  "name": "My Modpack",
  "buttons": [
    { "label": "Download", "url": "https://modrinth.com/modpack/my-modpack" },
    { "label": "Discord", "url": "https://discord.gg/example" }
  ]
}
```

- `application_id`: your own application from the [Discord Developer Portal](https://discord.com/developers/applications).
  Discord then shows its name and icon, and images uploaded under Rich Presence > Art Assets can be used by their
  name instead of a URL.
- `name`: shown after "Playing". Defaults to `Minecraft`, or to the name of your own application.
- `buttons`: up to two buttons with a label of at most 32 characters. They replace the download button. Discord
  does not show buttons while others can ask to join the player's world.

## One file per situation

| File | Shown |
|------|-------|
| `main_menu.json` | In the main menu |
| `options.json` | In the options |
| `multiplayer_menu.json` | In the server list |
| `singleplayer_menu.json` | In the world list |
| `create_world.json` | While creating a world |
| `menu.json` | In any other menu |
| `singleplayer.json` | While playing in a singleplayer world |
| `multiplayer.json` | While playing on a server |

```json
{
  "details": "Exploring {world}",
  "state": "{players} of {max_players} players",
  "large_image": "https://example.com/my-modpack.png",
  "large_text": "My Modpack 1.0"
}
```

- `details`: the first line.
- `state`: the second line.
- `large_image`: a URL of at most 256 characters, or the name of an art asset of your own application.
- `large_text`: shown when hovering the large image.

## Placeholders

| Placeholder | Value |
|-------------|-------|
| `{player}` | Name of the player |
| `{loader}` | `Fabric` or `NeoForge` |
| `{minecraft}` | Minecraft version |
| `{world}` | Name of the singleplayer world |
| `{server}` | Address of the server |
| `{players}` | Players in the world or on the server |
| `{max_players}` | Maximum number of players |

Players decide themselves whether their world name and server addresses are shown. A text that uses a hidden or
unknown value, for example `{server}` while the address is hidden or `{world}` on a server, shows the default
instead.
