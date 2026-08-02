# Block Outlines

Ein eigenständiger Fabric-Clientmod für **Minecraft 1.21.11**, der Blöcke mit frei
konfigurierbaren Umrandungen hervorhebt. Kein Crystal-/Combat-Zeug — nur Blöcke.

Alle Einstellungen werden im Spiel über **Mod Menu** gesetzt
(*Mods → Block Outlines → Zahnrad*) oder über eine frei wählbare Menü-Taste.

## Installation

1. Fabric Loader ≥ 0.18.4 für 1.21.11 installieren
2. `blockoutlines-1.21.11-1.0.0.jar` in den Ordner `mods` legen
3. Optional (empfohlen): [Mod Menu](https://modrinth.com/mod/modmenu) **17.0.0**
   für 1.21.11 dazulegen — damit gibt es den Einstellungs-Button

Fabric API wird **nicht** benötigt.

## Funktionen

### Block, auf den du schaust
| Einstellung | Beschreibung |
|---|---|
| Outline | `Vanilla` (normale schwarze Box), `Custom` (eigene Farbe), `Hidden` (ganz aus) |
| Outline colour | Farbe inkl. Transparenz (RGBA-Regler, Hex-Feld, Farbpaletten) |
| Line width | Linienstärke 0,5–10 px |
| Shape | echte Blockform (Treppen, Stufen, Zäune) oder immer voller Würfel |
| Box size | Box vergrößern/verkleinern (gegen Z-Fighting) |
| Fill | zusätzliche halbtransparente Füllung mit eigener Farbe |
| Through walls | Box bleibt durch Blöcke hindurch sichtbar |
| Rainbow | Farbverlauf statt fester Farbe |

### Blocksuche (ausgewählte Blöcke im Umkreis markieren)
| Einstellung | Beschreibung |
|---|---|
| Blocks… | Liste der markierten Blöcke; jeder Block bekommt **eigene Farbe** |
| Draw mode | nur Outline, nur Füllung oder beides |
| Default colour | Farbe für Blöcke ohne eigene Farbe |
| Line width / Fill opacity | Linienstärke und Deckkraft der Füllung |
| Range | Suchradius 8–256 Blöcke |
| Max blocks | Obergrenze gleichzeitig markierter Blöcke (FPS-Schutz) |
| Scan interval | wie oft die geladenen Chunks durchsucht werden (ms) |
| Shape / Box size | Blockform oder Würfel, Box vergrößern/verkleinern |
| Through walls | durch Wände sichtbar |
| Only exposed blocks | komplett eingeschlossene Blöcke überspringen |
| Merge touching blocks | zusammenhängende Blöcke zu einer großen Box zusammenfassen |
| Tracers | Linie vom Fadenkreuz zum Block, mit eigener Farbe und Stärke |
| Rainbow | Regenbogenfarben für alle Markierungen |

### Allgemein
* **Mod enabled** — Master-Schalter
* **Toggle key** / **Menu key** — frei belegbar (Taste im Menü anklicken, dann Taste drücken; `ESC` löscht die Belegung)
* **Chat message on toggle** — kurze Rückmeldung beim Ein-/Ausschalten
* **Rainbow speed**, **Fade with distance**
* **Reset all settings** — alles zurück auf Standard (Blockliste bleibt erhalten)

Die Konfiguration liegt in `config/blockoutlines.json` und kann auch von Hand
bearbeitet werden.

## Technische Details

* Gezeichnet wird über das Gizmo-System von 1.21.11 (`net.minecraft.gizmos`), das
  Minecraft selbst für Debug-Geometrie benutzt — dadurch stimmen Kamera-Offset,
  Nebel und Tiefentest ohne eigene Shader oder Render-Pipelines.
* Nur zwei Mixins: `LevelRenderer` (Geometrie ausgeben, Vanilla-Outline abschalten)
  und `KeyboardHandler` (Tastenkürzel).
* Die Chunk-Suche verwirft Sections über die Palette (`maybeHas`), bevor ein
  einziger Block angefasst wird, und läuft nur alle *Scan interval* Millisekunden.

## Bauen

```bash
cd blockoutlines
./gradlew build
# Ergebnis: build/libs/blockoutlines-1.21.11-1.0.0.jar
```
