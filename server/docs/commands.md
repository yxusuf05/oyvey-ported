# Commands

Everything a player can type. Admin commands are marked; they default to
operators and can be granted through any permission plugin.

## Playing

| Command | What it does |
|---|---|
| `/duel <player> [kit]` | Challenge someone directly |
| `/duel accept <player>` | Accept a challenge |
| `/leave` | Leave your match, or stop spectating |
| `/spectate <player>` | Watch someone's match |
| `/inv <id> <player>` | Open a post-match inventory (the end-of-match message links these) |
| `/stats [player]` | Record per kit |
| `/leaderboard [kit]` | Top players — aliases `/lb`, `/top` |
| `/ffa [join <id>\|leave\|list]` | Free-for-all arenas; no argument opens the menu |

Queues, the kit editor, leaderboards, settings and FFA are also reachable from
the hub hotbar.

## Party

| Command | What it does |
|---|---|
| `/party create` | Start a party |
| `/party invite <player>` | Invite someone |
| `/party join <leader>` | Accept an invite |
| `/party leave` | Leave your party |
| `/party kick <player>` | Remove a member (leader only) |
| `/party list` | Show members |
| `/party chat <message>` | Talk to the party — alias `/party c` |
| `/party split [kit]` | Two balanced teams from the party |
| `/party ffa [kit]` | Everyone against everyone |
| `/party disband` | End the party (leader only) |

Alias: `/p`.

## Survival

| Command | What it does |
|---|---|
| `/survival` | Travel to the survival world |
| `/spawn` | Back to the hub |
| `/sethome [name]` | Save a home where you stand |
| `/home [name]` | Teleport to a home |
| `/homes` | List your homes |
| `/delhome [name]` | Delete a home |
| `/tpa <player>` | Ask to teleport to someone |
| `/tpaccept` | Accept a request |

## Staff — `corepvp.staff`

| Command | What it does |
|---|---|
| `/staff` | Toggle staff mode (vanish + tools) |
| `/vanish` | Toggle vanish on its own |
| `/freeze <player>` | Freeze or unfreeze someone |

## Administration — `corepvp.command.admin`

| Command | What it does |
|---|---|
| `/corepvp help` | List subcommands |
| `/corepvp version` | Plugin and server version |
| `/corepvp reload` | Re-read every YAML file |
| `/corepvp setspawn [lobby\|survival]` | Set a spawn where you stand |
| `/corepvp arena generate <template> [count]` | Build arenas — templates: `flat`, `sumo`, `build`, `crystal` |
| `/corepvp arena list\|tp <id>\|delete <id>` | Manage arenas |
| `/corepvp kit list` | Show kits with their combat mode |
| `/corepvp kit give <id>` | Give yourself a kit |
| `/corepvp kit save <id>` | Overwrite a kit with your inventory |
| `/ffa create <id> <kit> [template]` | Build and register an FFA arena |
| `/ffa delete <id>` | Remove an FFA arena |

Aliases for the root command: `/cpvp`, `/practice`.

## Getting a server playable from nothing

```
/corepvp arena generate crystal 6
/corepvp arena generate flat 6
/corepvp arena generate sumo 4
/corepvp arena generate build 4
/ffa create crystal crystal
/corepvp setspawn
```

That is the whole setup — no world editing, no schematics.
