package me.danny.danworld;

import java.util.Arrays;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.Location;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.regions.Region;

public record Selection(Location min, Location max) {

  public static Selection fromWorldEditRegion(Region region) {
    var min = region.getMinimumPoint();
    var max = region.getMaximumPoint();
    var world = BukkitAdapter.adapt(region.getWorld());

    var minLoc = new Location(world, min.getX(), min.getY(), min.getZ());
    var maxLoc = new Location(world, max.getX(), max.getY(), max.getZ());
    return new Selection(minLoc, maxLoc);
  }

  public static Selection fromStrings(String... strings) {
    if(strings == null || strings.length != 3) return null;
    var world = Bukkit.getWorld(strings[0]);
    var min = strings[1];
    var max = strings[2];
    
    if(world == null || min == null || max == null || min.isBlank() || max.isBlank()) return null;

    var minParts = min.split(":");
    var maxParts = max.split(":");

    if(minParts.length != 3 || maxParts.length != 3) return null;

    var minComponents = parseParts(minParts);
    var maxComponents = parseParts(maxParts);

    if(minComponents.length != 3 || maxComponents.length != 3) return null;

    var minLoc = new Location(world, minComponents[0], minComponents[1], minComponents[2]);
    var maxLoc = new Location(world, maxComponents[0], maxComponents[1], maxComponents[2]);
    return new Selection(minLoc, maxLoc);
  }

  public static String[] intoStrings(Selection sel) {
    if(sel == null) return null;
    
    var world = sel.min().getWorld().getName();
    var min = "%d:%d:%d".formatted(sel.min().getBlockX(), sel.min().getBlockY(), sel.min().getBlockZ());
    var max = "%d:%d:%d".formatted(sel.max().getBlockX(), sel.max().getBlockY(), sel.max().getBlockZ());

    var out = new String[] { world, min, max };
    return out;
  }

  private static int[] parseParts(String[] parts) {
    return Arrays.stream(parts)
      .map(Selection::tryParseInt)
      .filter(Objects::nonNull)
      .mapToInt(i -> i)
      .toArray();
  }

  private static Integer tryParseInt(String maybeNum) {
    try {
      return Integer.parseInt(maybeNum);
    } catch(NumberFormatException _ignored) {
      return null;
    }
  }

  public boolean contains(double x, double y, double z) {
    return x >= min.getBlockX() && x <= max.getBlockX()
      && y >= min.getBlockY() && y <= max.getBlockY()
      && z >= min.getBlockZ() && z <= max.getBlockZ();
  }
}
