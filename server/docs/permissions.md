# Permissions

Nothing is required to play. Everything below is opt-in and defaults to
operators unless stated otherwise.

| Permission | Grants |
|---|---|
| `corepvp.admin` | Everything: includes the three below |
| `corepvp.command.admin` | `/corepvp` subcommands, `/ffa create`, `/ffa delete` |
| `corepvp.staff` | `/staff`, `/vanish`, `/freeze`, and seeing vanished players |
| `corepvp.build` | Building inside the hub and survival spawn protection |

## Ranks

Ranks are not permissions themselves — each rank in `ranks.yml` names the
permission that grants it, and a player gets the highest-priority rank they
hold. That works with LuckPerms, any other permission plugin, or plain
`permissions.yml`, with no integration needed.

| Permission | Rank |
|---|---|
| `corepvp.rank.vip` | VIP |
| `corepvp.rank.mvp` | MVP |
| `corepvp.rank.mod` | Mod |
| `corepvp.rank.admin` | Admin |
| `corepvp.rank.owner` | Owner |

Add your own by editing `ranks.yml`; priority decides both which rank wins and
where the player sorts in the tab list.

## Perks

| Permission | Effect |
|---|---|
| `corepvp.homes.<number>` | Raises the survival home limit to that number (up to 20) |
| `corepvp.ranked.bypass` | Skips the unranked-games requirement for ranked queues |
| `corepvp.spectate.bypass` | Spectate players who have spectators turned off |
| `corepvp.chat.bypass-cooldown` | No chat cooldown |
