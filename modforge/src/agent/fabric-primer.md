# Fabric 1.21.x primer (Mojang mappings) — follow this EXACTLY

The template uses **official Mojang mappings** (net.minecraft.world.item.Item etc.), Java 21, Fabric API. Training data is full of outdated Yarn-mapping and pre-1.21.2 patterns — do NOT use them.

## Project layout (fixed by the template — do not fight it)

- Entrypoint: `src/main/java/com/modforge/__PKG_ID__/ModEntry.java` implements `net.fabricmc.api.ModInitializer`. It already exists with `public static final String MOD_ID`. Extend its `onInitialize()` to call your init methods. Keep all classes in package `com.modforge.__PKG_ID__` (the literal token `__PKG_ID__` is substituted at build time — write it literally in paths and package declarations).
- Resources: `src/main/resources/assets/__MOD_ID__/...` and `src/main/resources/data/__MOD_ID__/...`. Use the literal token `__MOD_ID__` in paths, and `ModEntry.MOD_ID` in code — never hardcode a guessed id.
- `fabric.mod.json`, `build.gradle`, mixins json: already exist; do NOT write them.

## Items (1.21.2+ REQUIRES registry keys — old `new Item(new Item.Properties())` + register crashes)

```java
package com.modforge.__PKG_ID__;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import java.util.function.Function;

public final class ModItems {
    public static final Item RUBY = register("ruby", Item::new, new Item.Properties());

    private static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties props) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath(ModEntry.MOD_ID, name));
        Item item = factory.apply(props.setId(key));          // setId BEFORE constructing — mandatory since 1.21.2
        return Registry.register(BuiltInRegistries.ITEM, key, item);
    }

    public static void init() {} // forces static init; call from ModEntry.onInitialize()
}
```

- `ResourceLocation.fromNamespaceAndPath(ns, path)` — the constructor is private; `new ResourceLocation(...)` does not compile.
- Food: `new Item.Properties().food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(4).saturationModifier(0.3f).build())`.

## Creative tab entry (Fabric API)

```java
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.world.item.CreativeModeTabs;
// in init code:
ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.INGREDIENTS).register(e -> e.accept(ModItems.RUBY));
```

## Blocks (same key pattern; BlockItem needs its own key)

```java
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.item.BlockItem;

private static Block registerBlock(String name, Function<BlockBehaviour.Properties, Block> factory,
                                   BlockBehaviour.Properties props) {
    ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(ModEntry.MOD_ID, name));
    Block block = factory.apply(props.setId(key));
    Registry.register(BuiltInRegistries.BLOCK, key, block);
    ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(ModEntry.MOD_ID, name));
    Registry.register(BuiltInRegistries.ITEM, itemKey,
            new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
    return block;
}
// usage: registerBlock("ruby_block", Block::new, BlockBehaviour.Properties.of().strength(3.0f));
```

## Commands (Fabric Command API v2)

```java
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, env) ->
    dispatcher.register(Commands.literal("hello")
        .executes(ctx -> { ctx.getSource().sendSuccess(() -> Component.literal("Hi!"), false); return 1; })));
```

## Common events (Fabric API, package net.fabricmc.fabric.api.event...)

- `ServerTickEvents.END_SERVER_TICK.register(server -> ...)` (lifecycle.v1)
- `ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> ...)` (networking.v1)
- `PlayerBlockBreakEvents.AFTER.register(...)` (player interaction, event.player)

## Asset JSON — 1.21.4+ needs BOTH files per item

1. Client item definition `assets/__MOD_ID__/items/ruby.json`:
```json
{ "model": { "type": "minecraft:model", "model": "__MOD_ID__:item/ruby" } }
```
2. Model `assets/__MOD_ID__/models/item/ruby.json`:
```json
{ "parent": "minecraft:item/generated", "textures": { "layer0": "__MOD_ID__:item/ruby" } }
```
3. Texture `assets/__MOD_ID__/textures/item/ruby.png` (via write_texture when available; a missing texture is ugly but does not crash — never block a build on it).
4. Lang `assets/__MOD_ID__/lang/en_us.json`: `{ "item.__MOD_ID__.ruby": "Ruby" }` (blocks: `block.__MOD_ID__.<name>`).

Blocks additionally: `assets/__MOD_ID__/blockstates/<name>.json` (`{"variants":{"":{"model":"__MOD_ID__:block/<name>"}}}`) and `models/block/<name>.json` (`{"parent":"minecraft:block/cube_all","textures":{"all":"__MOD_ID__:block/<name>"}}`), plus `models/item/<name>.json` with `{"parent":"__MOD_ID__:block/<name>"}` and an items/ definition referencing it.

## Recipes

`data/__MOD_ID__/recipe/<name>.json` — the folder is singular `recipe` since 1.21 (NOT `recipes`). Example shaped:
```json
{ "type": "minecraft:crafting_shaped", "pattern": ["##","##"], "key": {"#": "__MOD_ID__:ruby"}, "result": {"id": "__MOD_ID__:ruby_block", "count": 1} }
```
Ingredients/results use plain id strings (`"id"` for result) — not `{"item": ...}` objects in 1.21.2+.

## Forbidden stale patterns (these DO NOT COMPILE or crash on 1.21.11)

- `new ResourceLocation(ns, path)` / `new Identifier(...)` → use `ResourceLocation.fromNamespaceAndPath`.
- Registering items/blocks without `ResourceKey` + `props.setId(key)`.
- Yarn names (`Item.Settings`, `FabricItemSettings`, `Registry.ITEM`, `Text.literal` etc.) — this project uses Mojang mappings (`Item.Properties`, `BuiltInRegistries.ITEM`, `Component.literal`).
- `FabricItemGroupBuilder`, `ItemGroup.BUILDER` statics → use `ItemGroupEvents` (or `FabricItemGroup.builder()` for a whole new tab).
- `data/<id>/recipes/` folder, `"item":`/`"count":` result objects in recipes.
- Mixins for things Fabric API has events for. Only add a mixin if truly necessary; then also add the class name to `src/main/resources/__MOD_ID__.mixins.json` (write the whole file).
