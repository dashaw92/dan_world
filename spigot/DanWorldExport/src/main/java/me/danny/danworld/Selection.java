package me.danny.danworld;

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

}
