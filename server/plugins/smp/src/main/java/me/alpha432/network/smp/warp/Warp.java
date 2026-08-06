package me.alpha432.network.smp.warp;

import org.bukkit.Location;
import org.bukkit.Material;

/**
 * A public teleport target.
 *
 * @param permission {@code null} when everybody may use it
 */
public record Warp(String name, Location location, String permission, String description, Material icon) {
}
