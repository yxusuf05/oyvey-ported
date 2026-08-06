# Minecraft-Netzwerk — Lobby · SMP · Practice

Ein komplettes Server-Setup für **Paper 1.21.11**, bestehend aus sechs selbst geschriebenen
Plugins. Kein Hosting, keine Fremd-Plugins — nur der Code, die Konfigurationen und ein
Start-Skript, mit dem der Server lokal läuft.

Spieler joinen in die **Lobby** und wählen dort über den Kompass, ob sie in den **SMP**
(Bauen, Economy, Shop, Homes) oder in den **Practice**-Bereich (Duelle, Ranked mit ELO, FFA)
wollen. Alles läuft auf einem Server mit mehreren Welten — kein Proxy nötig.

---

## Schnellstart

```bash
# 1. Plugins bauen und in den Test-Server kopieren (vom Repo-Root aus)
./gradlew -p server installPlugins

# 2. EULA akzeptieren (nur einmal nötig)
cd server/runtime
echo "eula=true" > eula.txt      # https://aka.ms/MinecraftEULA

# 3. Starten — die Paper-Jar wird beim ersten Mal automatisch geladen
./start.sh                        # Windows: start.bat
```

Danach im Minecraft-Client (1.21.11) auf `localhost` verbinden.

**Voraussetzungen:** Java 21 (`java -version`), rund 4 GB RAM für den Server. Der Arbeitsspeicher
lässt sich über die Umgebungsvariablen `MIN_RAM` und `MAX_RAM` anpassen.

---

## Ersteinrichtung im Spiel

Mach dich zuerst zum Operator (in der Server-Konsole):

```
op DEIN_NAME
rank set DEIN_NAME owner
```

Danach die Welten einrichten:

| Schritt | Befehl | Wo |
|---|---|---|
| Lobby-Spawn setzen | `/setlobby` | in der Welt `lobby` |
| SMP-Spawn setzen | `/setspawn` | in der Welt `smp` |
| Practice-Hub setzen | `/arena sethub` | in der Welt `practice` |
| Spawn-Schutz im SMP | `/region pos1`, `/region pos2`, `/region create spawn 10`, dann `/region flag spawn build false` und `/region flag spawn pvp false` | in der Welt `smp` |

**Duell-Arena anlegen** (in der Welt `practice`, du baust die Arena selbst):

```
/arena create arena1
/arena spawn1 arena1          # an Position von Spieler 1 stehen
/arena spawn2 arena1          # an Position von Spieler 2 stehen
/arena pos1                   # eine Ecke des Kampfbereichs
/arena pos2                   # die gegenüberliegende Ecke
/arena bounds arena1          # Ecken übernehmen (wichtig für BuildUHC-Rollback)
/arena kit arena1 nodebuff    # Kits freischalten, ohne Angabe gilt sie für alle
/arena list
```

**FFA-Arena:** an die gewünschte Spawn-Position stellen und `/arena setffa nodebuff` ausführen.
Die FFA-Arenen selbst stehen in `plugins/NetworkPractice/ffa.yml`.

---

## Die sechs Plugins

| Plugin | Aufgabe |
|---|---|
| **NetworkCore** | Basis für alle anderen: Datenbank, Spielerprofile, Ränge, Economy, GUI-Framework, Befehls-Framework, Welten, Teleports, Regionen, Scoreboard und Tab |
| **NetworkChat** | Chat-Format, Erwähnungen, Anti-Spam, Join/Leave, `/msg`, Tab-Liste, Standard-Sidebars |
| **NetworkLobby** | Hub: Spawn, Kompass-Auswahl, Schutz, Doppelsprung, Spieler ausblenden |
| **NetworkSMP** | Spawn, Homes, Warps, `/tpa`, `/back`, `/rtp`, Kits, Economy, Shop |
| **NetworkPractice** | Duelle, Ranked mit ELO, FFA, Kit-Editor, Statistiken, Bestenlisten |
| **NetworkStaff** | Moderation: Kick, Bann, Mute, Verwarnung, Strafhistorie, Team-Chat |

`NetworkCore` muss immer installiert sein, die anderen fünf sind einzeln abschaltbar (einfach
die `.jar` aus `plugins/` entfernen).

---

## Ränge

Es gibt zwei Familien, definiert in `plugins/NetworkCore/ranks.yml`.

### Kaufbare Ränge

Mit Ingame-Geld über `/rank menu` oder `/rank buy <rang>`. Jede Stufe erbt die Vorteile der
vorherigen.

| Rang | Preis | Vorteile |
|---|---|---|
| Member | — | 2 Homes, Starter-Kit, 1 eigenes Practice-Kit |
| Silber | 5.000 | 4 Homes, Silber-Kit (6 h), +5 % Verkaufserlös, 2 Practice-Kits, Sprung-Boost |
| Emerald | 25.000 | 6 Homes, Emerald-Kit (4 h), +10 %, 4 Practice-Kits, bevorzugte Queue |
| Diamant | 100.000 | 10 Homes, Diamant-Kit (3 h), +20 %, 6 Practice-Kits, halbe Teleport-Wartezeit, Fliegen in der Lobby |
| Netherite | 500.000 | 15 Homes, Netherite-Kit (2 h), +35 %, 9 Practice-Kits, Teleport ohne Wartezeit, Chat-Farben |

### Team-Ränge

Nicht kaufbar, werden mit `/rank set <spieler> <rang>` vergeben. Dafür braucht man
`network.command.rank.staff` — also SrAdmin oder Owner.

| Rang | Kann zusätzlich |
|---|---|
| Helper | `/kick`, `/mute`, `/warn`, `/history`, `/staffchat` |
| Moderator | `/tempban`, `/tempmute`, `/unmute`, `/banlist`, `/chat clear` und `/chat mute` |
| Sr. Moderator | permanente Banns, alle Strafen einsehen, Region-Bypass |
| Admin | `/unban`, Ränge vergeben (ohne Team-Ränge), Regionen, Warps, Kits, Shop |
| Sr. Admin | Team-Ränge vergeben, `/network reload`, Economy-Verwaltung |
| Owner | alles |

Ein Teammitglied kann niemanden mit gleichem oder höherem Rang bestrafen.

Im Practice-Bereich kommt ein **automatischer ELO-Titel** dazu (Bronze → Silber → Gold →
Diamant → Champion). Der wird nicht vergeben, sondern erspielt.

---

## Befehle

### Überall
`/rank menu` · `/rank buy <rang>` · `/rank list` · `/msg <spieler> <text>` · `/reply` ·
`/ignore <spieler>` · `/lobby`

### SMP
`/spawn` · `/home [name]` · `/sethome [name]` · `/delhome <name>` · `/homes` ·
`/warp [name]` · `/warps` · `/tpa <spieler>` · `/tpahere <spieler>` · `/tpaccept` · `/tpdeny` ·
`/back` · `/rtp` · `/kit [name]` · `/balance [spieler]` · `/pay <spieler> <betrag>` ·
`/baltop` · `/shop` · `/sell hand|all`

### Practice
`/practice` · `/duel <spieler> [kit]` · `/accept` · `/leave` · `/spectate <spieler>` ·
`/stats [spieler]` · `/leaderboard` · `/ffa [arena]`

### Team
`/kick` · `/ban` · `/tempban <spieler> <dauer>` · `/unban` · `/mute` · `/tempmute` · `/unmute` ·
`/warn` · `/history <spieler>` · `/banlist` · `/staffchat`

Dauer-Angaben: `30m`, `6h`, `7d`, `2w`, `1y`, auch kombiniert (`1d12h`). `perm` ist permanent.

### Verwaltung
`/setlobby` · `/setspawn` · `/setwarp <name>` · `/delwarp <name>` · `/eco <give|take|set>` ·
`/region <pos1|pos2|create|flag|priority|list|here>` · `/arena <…>` · `/network <reload|save|info>` ·
`/chat <clear|mute|unmute|reload>`

---

## Konfiguration

Alle Dateien liegen nach dem ersten Start unter `runtime/plugins/<Plugin>/`.

| Datei | Inhalt |
|---|---|
| `NetworkCore/config.yml` | Datenbank (SQLite oder MySQL), Economy, Teleport, Scoreboard-Takt |
| `NetworkCore/ranks.yml` | alle Ränge, Preise, Rechte und Vorteilstexte |
| `NetworkCore/worlds.yml` | welche Welten erzeugt werden, inkl. Void-Generator und Spielregeln |
| `NetworkCore/regions.yml` | geschützte Bereiche (über `/region` gepflegt) |
| `NetworkChat/config.yml` | Chat-Format, Tab-Liste, Sidebars pro Welt |
| `NetworkLobby/config.yml` | Lobby-Welt, Schutz, Doppelsprung, Kompass-Menü |
| `NetworkSMP/kits.yml` | SMP-Kits mit Cooldowns |
| `NetworkSMP/shop.yml` | Shop-Kategorien mit Kauf- und Verkaufspreisen |
| `NetworkPractice/kits.yml` | Practice-Kits inkl. Kampfregeln |
| `NetworkPractice/arenas.yml` | Duell-Arenen (über `/arena` gepflegt) |
| `NetworkPractice/ffa.yml` | FFA-Arenen |
| `NetworkStaff/config.yml` | Strafen-Einstellungen, gesperrte Befehle bei Mute |

Alle Texte stehen in der jeweiligen `messages.yml` und nutzen
[MiniMessage](https://docs.advntr.dev/minimessage/format.html).

Was der Server im Ordner `runtime/` selbst erzeugt — Welten, Logs, die Paper-Jar, `eula.txt`
und die Plugin-Daten — ist per `.gitignore` ausgenommen. Versioniert sind nur die Start-Skripte
und die vorbereiteten Server-Konfigurationen. **Achtung:** Paper schreibt bei jedem Start einen
neuen `management-server-secret` in die `server.properties`. Der Eintrag ist hier absichtlich
leer und gehört nicht ins Repository — beim Committen also nicht mit übernehmen
(`git checkout -- server/runtime/server.properties`).

**Datenbank:** standardmäßig SQLite in `runtime/plugins/NetworkCore/data.db` — nichts zu
installieren. Für MySQL in `NetworkCore/config.yml` auf `type: mysql` stellen und den
MySQL-Treiber in den `libraries`-Ordner des Servers legen. Fehlt der Treiber, fällt der Server
mit einer Warnung auf SQLite zurück statt abzustürzen.

---

## Entwicklung

```bash
./gradlew -p server build            # alles bauen und Tests laufen lassen
./gradlew -p server test             # nur Tests
./gradlew -p server installPlugins   # Jars nach runtime/plugins kopieren
./gradlew -p server :practice:build  # nur ein Plugin
```

Der Server-Build liegt unter `server/` und ist vom Fabric-Client-Mod im Repo-Root komplett
getrennt — beide haben eigene Gradle-Projekte und stören sich nicht.

**Aufbau:** `server/plugins/<modul>/src/main/java/me/alpha432/network/<modul>/`.
Neue Befehle erben von `BaseCommand`, neue GUIs von `Menu` oder `PagedMenu`, Texte laufen über
`Messages`/`Msg`, Items über `ItemParser` und `ItemBuilder` — alles aus `NetworkCore`.

**Tests** decken die Bukkit-freie Logik ab: ELO-Berechnung, Statistik-Fortschreibung,
Regionen-Geometrie, Cooldowns und das Parsen von Strafdauern.
