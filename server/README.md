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
| Crate aufstellen | Block ansehen, `/crate setblock vote` | in der Welt `smp` |
| Kampfbereiche | Grenzen in `fightareas.yml` eintragen, dann `/fightarea reload` und `/fightarea setspawn sword` | in der Welt `smp` |

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

### Shop-Ränge (echtes Geld)

Werden im Webshop gekauft. `/rank menu` zeigt Preis und Vorteile, der Klick liefert nur den
Shop-Link — das Plugin verarbeitet **keine** Zahlungen. Nach dem Kauf führt der Shop
`rank set <spieler> <rang>` über die Konsole aus. Jede Stufe erbt die Vorteile der vorherigen.

| Rang | Preis | Vorteile |
|---|---|---|
| Member | — | 2 Homes, Starter-Kit, 1 eigenes Practice-Kit |
| Silber | 4,99 € | 4 Homes, Silber-Kit (6 h), +5 % Verkaufserlös, 2 Practice-Kits, Sprung-Boost |
| Emerald | 9,99 € | 6 Homes, Emerald-Kit (4 h), +10 %, 4 Practice-Kits, bevorzugte Queue |
| Diamant | 19,99 € | 10 Homes, Diamant-Kit (3 h), +20 %, 6 Practice-Kits, halbe Teleport-Wartezeit, Fliegen in der Lobby |
| Netherite | 39,99 € | 15 Homes, Netherite-Kit (2 h), +35 %, 9 Practice-Kits, Teleport ohne Wartezeit, Chat-Farben |

Preise, Währung und Shop-Link stehen in `NetworkCore/config.yml` unter `rank-shop`, die Ränge
selbst in `NetworkCore/ranks.yml`.

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

### Practice-Ränge (erspielt)

Im Practice-Bereich gibt es eine **eigene, komplett kostenlose Rangleiter**: Bronze → Silber →
Gold → Diamant → Champion. Sie hängt allein am ELO aus der Ranked-Queue, lässt sich nicht
kaufen und nicht vergeben. Die Grenzen stehen in `Elo.tier(...)`, die Anzeigenamen in
`NetworkPractice/messages.yml` unter `tier`.

Unranked und Ranked gibt es beide: Unranked zählt nur Siege und Niederlagen, Ranked bewegt
zusätzlich das ELO und damit den Practice-Rang.

---

## Webshop anbinden

Zahlungen laufen komplett beim Shop-Anbieter (Tebex, CraftingStore, …). Die Plugins stellen nur
die Konsolen-Befehle bereit, die der Shop nach einem Kauf ausführt:

| Kauf | Befehl, den der Shop ausführt |
|---|---|
| Rang | `rank set %player% netherite` |
| Crate-Keys | `key give %player% legendaer 3` |
| Key-All-Aktion | `key giveall vote 1` |

Trage im Shop den Server als Konsolen-Ziel ein und hinterlege genau diese Befehle. Es ist kein
weiterer Code nötig und kein Zahlungsdaten-Handling im Plugin.

---

## Crates am SMP-Spawn

Crates sind Blöcke am Spawn, die mit einem passenden Key geöffnet werden. Der Key ist ein
normales Item mit unsichtbarer Markierung — umbenennen oder nachbauen bringt nichts.

```
/crate preview              # alle Crates mit deinen Keys auflisten
/crate preview legendaer    # Gewinne samt echter Prozentchance ansehen
/crate setblock vote        # Crate auf den Block setzen, den du ansiehst
```

Rechtsklick auf die Crate öffnet sie mit Animation, Linksklick zeigt nur die Vorschau. Fehlt
der Key, öffnet sich automatisch die Vorschau. Gewinne, Chancen (`weight`) und optionale
Konsolen-Befehle stehen in `NetworkSMP/crates.yml`; seltene Gewinne mit `broadcast: true`
werden serverweit angekündigt.

---

## Kampfbereiche am SMP-Spawn

Zwei PvP-Zonen direkt am Spawn, konfiguriert in `NetworkSMP/fightareas.yml`:

- **Sword-Area** (`type: SWORD`) — es zählen nur die Waffen aus `allowed-items` (ab Werk
  Schwerter und die Mace). Bauen und Abbauen sind komplett gesperrt.
- **Crystal-Area** (`type: CRYSTAL`) — Bauen erlaubt, und alle zehn Minuten
  (`reset-seconds: 600`) wird der Bereich automatisch in den Ausgangszustand zurückgesetzt.
  Auch Krater von Crystals, Eimer-Wasser und Feuer werden mitgeschnitten und zurückgerollt.

Grenzen setzt du mit `/region pos1` und `/region pos2` (die Koordinaten werden angezeigt),
trägst sie unter `bounds` ein und lädst mit `/fightarea reload` neu. Einstiegspunkt setzen:
`/fightarea setspawn sword`, betreten mit `/fightarea join sword`, sofort zurücksetzen mit
`/fightarea reset crystal`.

---

## Practice: PvP-Wildnis und Trainings-Bot

**`/rtp` im Practice-Bereich** führt in eine eigene Welt (`practice_pvp`) mit hügeligem Gelände
und Bäumen — aber ohne Höhlen, ohne Wasser und ohne Mobs, damit nichts einen Kampf entscheidet
außer dem Kampf. Höhe, Hügelbreite und Baumdichte stellst du unter `pvp-world` in
`NetworkPractice/config.yml` ein. Derselbe Befehl bringt dich im SMP in die normale Wildnis —
welches Ziel du bekommst, entscheidet die Welt, in der du stehst.

**`/bot`** ruft einen Trainingsgegner. Er wird komplett von diesem Plugin gesteuert, ohne
Server-Interna, und überlebt damit auch MC-Updates.

```
/bot spawn [kit]        # Bot rufen, du bekommst dasselbe Kit
/bot difficulty 1-5     # Reaktionszeit, Trefferquote und Tempo
/bot mode <modus>       # aggressive, defensive, strafe, sumo, boxing
/bot kit <kit>          # Kit im laufenden Training wechseln
/bot health 20          # Lebenspunkte (20 = 10 Herzen)
/bot knockback 1.5      # wie weit der Bot von deinen Treffern fliegt
/bot ping 100           # simulierte Reaktionsverzögerung in Millisekunden
/bot stats              # Einstellungen und Auswertung
/bot stop               # beenden, mit Zusammenfassung
```

Während des Trainings zeigt die Actionbar Combo, beste Combo, Treffer, Genauigkeit und das
Leben des Bots; am Ende kommen zusätzlich kassierte Treffer und Hits pro Sekunde.

---

## Befehle

### Überall
`/rank menu` · `/rank buy <rang>` · `/rank list` · `/msg <spieler> <text>` · `/reply` ·
`/ignore <spieler>` · `/lobby`

### SMP
`/spawn` · `/home [name]` · `/sethome [name]` · `/delhome <name>` · `/homes` ·
`/warp [name]` · `/warps` · `/tpa <spieler>` · `/tpahere <spieler>` · `/tpaccept` · `/tpdeny` ·
`/back` · `/rtp` · `/kit [name]` · `/balance [spieler]` · `/pay <spieler> <betrag>` ·
`/baltop` · `/shop` · `/sell hand|all` · `/crate preview` · `/key check` ·
`/fightarea join <name>`

### Practice
`/practice` · `/duel <spieler> [kit]` · `/accept` · `/leave` · `/spectate <spieler>` ·
`/stats [spieler]` · `/leaderboard` · `/ffa [arena]` · `/rtp` · `/bot <…>`

### Team
`/kick` · `/ban` · `/tempban <spieler> <dauer>` · `/unban` · `/mute` · `/tempmute` · `/unmute` ·
`/warn` · `/history <spieler>` · `/banlist` · `/staffchat`

Dauer-Angaben: `30m`, `6h`, `7d`, `2w`, `1y`, auch kombiniert (`1d12h`). `perm` ist permanent.

### Verwaltung
`/setlobby` · `/setspawn` · `/setwarp <name>` · `/delwarp <name>` · `/eco <give|take|set>` ·
`/region <pos1|pos2|create|flag|priority|list|here>` · `/arena <…>` · `/network <reload|save|info>` ·
`/chat <clear|mute|unmute|reload>` · `/crate setblock <crate>` · `/key give <spieler> <crate> [n]` ·
`/key giveall <crate> [n]` · `/fightarea <setspawn|reset|reload>`

---

## Konfiguration

Alle Dateien liegen nach dem ersten Start unter `runtime/plugins/<Plugin>/`.

| Datei | Inhalt |
|---|---|
| `NetworkCore/config.yml` | Datenbank (SQLite oder MySQL), Economy, Teleport, Scoreboard-Takt |
| `NetworkCore/ranks.yml` | alle Ränge, Preise, Rechte und Vorteilstexte |
| `NetworkCore/config.yml` → `rank-shop` | Shop-Link, Währung und Preisformat |
| `NetworkCore/worlds.yml` | welche Welten erzeugt werden, inkl. Void-Generator und Spielregeln |
| `NetworkCore/regions.yml` | geschützte Bereiche (über `/region` gepflegt) |
| `NetworkChat/config.yml` | Chat-Format, Tab-Liste, Sidebars pro Welt |
| `NetworkLobby/config.yml` | Lobby-Welt, Schutz, Doppelsprung, Kompass-Menü |
| `NetworkSMP/kits.yml` | SMP-Kits mit Cooldowns |
| `NetworkSMP/shop.yml` | Shop-Kategorien mit Kauf- und Verkaufspreisen |
| `NetworkSMP/crates.yml` | Crates, Keys, Gewinne und deren Chancen |
| `NetworkSMP/fightareas.yml` | Sword- und Crystal-Bereich am Spawn inkl. Reset-Intervall |
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

**Tests** (38 Stück) decken die Bukkit-freie Logik ab: ELO-Berechnung, Statistik-Fortschreibung,
Regionen-Geometrie, Cooldowns, das Parsen von Strafdauern, die Bot-Einstellungen und den
Gelände-Generator der PvP-Welt.
