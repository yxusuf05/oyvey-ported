# ⛏️ ModForge — KI-Minecraft-Mod-Generator

Ein selbst hostbarer Chat-Dienst: Nutzer beschreiben einen Minecraft-Mod oder ein Texture-Pack, die KI stellt Rückfragen, schreibt den Code selbst, kompiliert ihn — und legt die fertige **.jar** (Fabric-Mod) bzw. **.zip** (Resource-Pack) als Download in den Chat.

- **Free-Plan:** schwächeres KI-Modell, 20 Nachrichten & 5 Builds pro Tag, kleine Projekte
- **Premium-Plan:** stärkeres KI-Modell (Claude Sonnet), 200 Nachrichten & 40 Builds pro Tag, größere Projekte, KI-Texturen in Mods

## Voraussetzungen

- **Node.js 20+**
- **Java 21** (JDK) — für die Gradle-Builds der Mods
- Ein KI-Backend:
  - **Anthropic-API-Key** (beste Qualität; [console.anthropic.com](https://console.anthropic.com)), **oder**
  - **kostenlos lokal** via [Ollama](https://ollama.com): `ollama pull qwen3-coder:30b` (braucht ~24 GB RAM oder eine gute GPU; Qualität schwächer, mehr fehlgeschlagene Builds)
- Internetzugang zu `maven.fabricmc.net`, `libraries.minecraft.net`, `repo.maven.apache.org` und `services.gradle.org` (für die Mod-Builds)

## Setup

```bash
cd modforge
npm install
cp .env.example .env        # und ausfüllen (mind. ein KI-Backend)
npm run warm-gradle         # einmalig: lädt Gradle + alle Fabric-Abhängigkeiten (dauert ein paar Minuten)
npm start                   # → http://localhost:3000
```

`npm run warm-gradle` baut das leere Mod-Template einmal durch. Danach dauern Nutzer-Builds nur noch ~30–90 s statt Minuten.

## Wie es funktioniert

1. Nutzer registriert sich (E-Mail + Passwort) und erstellt ein Projekt (Mod oder Resource-Pack).
2. Er beschreibt im Chat, was er will. Die KI stellt bei Unklarheiten **Rückfragen** (mit klickbaren Antwort-Chips) — das ist eingebaut, damit keine Fehl-Generierungen passieren.
3. Die KI schreibt die Java-/Asset-Dateien in einen virtuellen Workspace (sie kann **nur** `src/**` und `assets/**` schreiben — nie Build-Skripte), lässt den Server per Gradle bauen, bekommt bei Compile-Fehlern einen kompakten Fehlerbericht und fixt iterativ.
4. Die fertige Datei erscheint als **Download-Karte im Chat**. Mod-Jars kommen in den `mods/`-Ordner eines Fabric-Clients (Fabric Loader + Fabric API nötig), Resource-Packs in `resourcepacks/`.

Texturen entstehen als Pixel-Art: Die KI beschreibt Paletten + Pixelraster, der Server rendert daraus echte PNGs.

## Architektur

```
server.js            Express-Server (API, SSE, statisches Frontend)
config.js            alle Einstellungen & Plan-Limits
db.js                SQLite-Schema (better-sqlite3, eine Datei: data/modforge.db)
src/agent/           Agent-Loop, Tools, System-Prompts, Fabric-Primer, Provider-Abstraktion
src/build/           Workspace-Assembly, Gradle-Runner, Build-Queue, Fehler-Parser
src/textures/        Pixelraster→PNG-Renderer, Resource-Pack-Zipper
template/            versionierte Mod-Templates (aktuell: fabric-1.21.11)
public/              Chat-Frontend (Vanilla JS, kein Build-Step)
scripts/             warm-gradle, verify-e2e
```

**Neue Minecraft-Versionen / Loader ergänzen:** Template-Verzeichnis unter `template/` kopieren, Versionen in dessen `gradle.properties` + `template.json` anpassen, fertig — der Rest des Systems liest alles aus `template.json`.

**KI-Backend wechseln:** nur `.env`. Beide Pläne können unterschiedliche Provider nutzen (z.B. Free lokal über Ollama, Premium über Anthropic).

## Stripe (echte Zahlungen)

Ohne Keys läuft alles im Stub-Modus (der Upgrade-Button erklärt das; mit `ALLOW_DEV_UPGRADE=1` gibt es einen Test-Schalter). Für echte Zahlungen:

1. Stripe-Konto anlegen, ein Abo-Produkt „Premium" erstellen → `STRIPE_PRICE_ID_PREMIUM`
2. `STRIPE_SECRET_KEY` setzen
3. Webhook auf `https://deine-domain/api/billing/webhook` einrichten (Events: `checkout.session.completed`, `customer.subscription.deleted`) → `STRIPE_WEBHOOK_SECRET`

## Sicherheit / Betrieb

- Die KI kompiliert Java-Code, führt ihn aber **nicht aus**. Build-Skripte kann sie nicht verändern (Pfad-Allowlist). Für öffentliches Hosting solltest du die Builds zusätzlich in einen Container/eine Sandbox sperren und HTTPS + einen Reverse-Proxy davorsetzen.
- Alle Daten liegen in `data/` (SQLite + Artefakte) — das Verzeichnis sichern genügt als Backup.
- `npm run verify` führt die deterministischen Selbsttests aus (Assembly, Fehler-Parser, Textur-Renderer, Pack-Zipper, Stub-Build-Pipeline).
- `npm run smoke` startet Server + Mock-KI und testet den kompletten Ablauf über die echte HTTP-API (Signup → Projekt → Rückfrage → Build → Download → Limits → Upgrade).
- `VERIFY_REAL_BUILD=1 npm run verify` lässt den Fixture-Mod zusätzlich durch das **echte** Gradle/Fabric-Toolchain laufen (braucht die o.g. Netzwerk-Hosts; einmal auf deinem Server ausführen, um den Build-Pfad zu bestätigen).

## Bekannte Grenzen (MVP)

- Nur **Fabric**, aktuell Minecraft **1.21.11** (weitere Templates einfach ergänzbar; Forge/NeoForge geplant).
- Texturen sind 16×16/32×32-Pixel-Art — keine fotorealistischen Texturen.
- Ein Build zur Zeit (Warteschlange); auf stärkeren Servern `BUILD_CONCURRENCY` erhöhen.
