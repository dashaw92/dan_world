package me.danny.danworld;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;
import java.util.zip.GZIPOutputStream;

import org.bukkit.block.Biome;
import org.bukkit.plugin.java.JavaPlugin;

public final class DanWorld {

  public static boolean save(Selection sel, String name) {
    var l = genLogger(name);
    
    var version = 1;
    var width = Math.ceilDiv(Math.abs(sel.max().getBlockX() - sel.min().getBlockX()), 16);
    var depth = Math.ceilDiv(Math.abs(sel.max().getBlockZ() - sel.min().getBlockZ()), 16);
    
    l.accept("Region to export is %dx%d (width x depth).".formatted(width, depth));

    byte[] bytes;
    try(var b = new ByteArrayOutputStream(); var gz = new GZIPOutputStream(b); var d = new DataOutputStream(gz)) {
      writeStrings(d, List.of("DanWorld"));
      d.writeByte((byte)version);
      d.writeShort((short)width);
      d.writeShort((short)depth);

      
      for(int x = 0; x < width; x++) {
        for(int z = 0; z < depth; z++) {
          writeChunk(l, d, 16 * x, 16 * z, sel);
        }
      }

      d.close();
      bytes = b.toByteArray();
    } catch(IOException e) {
      l.accept("Failed to serialize to bytes: %s".formatted(e.getMessage()));
      return false;
    }
    
    var f = getFile(name);
    try(var fw = new FileOutputStream(f)) {
      fw.write(bytes);
    } catch(Exception e) {
      l.accept("Failed to save bytes to file: %s".formatted(e.getMessage()));
      return false;
    }

    l.accept("World saved successfully.");
    return true;
  }

  private static File getFile(String name) {
    var plug = JavaPlugin.getPlugin(DanWorldExportPlugin.class);
    var f = new File(plug.getDataFolder(), name);
    f.getParentFile().mkdirs();
    return f;
  }

  private static Consumer<String> genLogger(String name) {
    var plug = JavaPlugin.getPlugin(DanWorldExportPlugin.class);
    return (msg) -> plug.getLogger().info("[Export of <%s>]: %s".formatted(name, msg));
  }


  private static void writeChunk(Consumer<String> l, DataOutputStream d, int cx, int cz, Selection sel) throws IOException {
    l.accept("Writing chunk (%d, %d)...".formatted(cx / 16, cz / 16));
    
    var numSections = Math.ceilDiv(sel.max().getBlockY() - sel.min().getBlockY(), 16);
    d.writeByte((byte)numSections);
    
    l.accept("Chunk (%d, %d) has %d sections.".formatted(cx, cz, numSections));
    for(int y = 0; y < numSections; y++) {
      writeChunkSection(l, d, sel, cx, cz, y);
    }
  }

  private static void writeChunkSection(Consumer<String> l, DataOutputStream d, Selection sel, int cx, int cz, int sectionY) throws IOException {
    var world = sel.min().getWorld();
    record Vec3(int x, int y, int z) {}

    //Record all unique materials
    var unique = new HashSet<String>();
    //Keep blocks visited in order
    var locs = new ArrayList<Vec3>();
    //And map the location to a material key
    var blocks = new HashMap<Vec3, String>();
    var biomes = new HashMap<Vec3, Biome>();

    //Localize block lookups to the current chunk
    var bx = sel.min().getBlockX() + cx;
    var bz = sel.min().getBlockZ() + cz;
    //And section Y (chunk sections)
    var by = sel.min().getBlockY() + (sectionY * 16);

    for(int y = 0; y < 16; y++) {
      for(int x = 0; x < 16; x++) {
        for(int z = 0; z < 16; z++) {
          if(bx + x >= sel.max().getBlockX() || bz + z >= sel.max().getBlockZ() || by + y >= sel.max().getBlockY()) continue;
          
          var v = new Vec3(x, y, z);
          var block = world.getBlockAt(bx + x, by + y, bz + z);
          var matKey = block.getType().getKey();

          locs.add(v);
          blocks.put(v, matKey.getKey());
          biomes.put(v, world.getBiome(block.getLocation()));
          unique.add(matKey.getKey());
        }
      }
    }

    l.accept("Chunk section y %d block data retrieved. Serializing.".formatted(sectionY));
    //Generate the palette. Order must be stable.
    var palette = new ArrayList<>(unique);

    d.writeByte(palette.size());
    l.accept("Palette size being encoded is %d.".formatted(palette.size()));
    l.accept("Palette is " + palette);
    writeStrings(d, palette);

    d.writeShort(locs.size());
    l.accept("Saved %d blocks from this chunk section.".formatted(locs.size()));
    for(var vec : locs) {
      //Since iteration order is always xz per section, location data isn't needed
      //Write only the palette index of this block. The world loader will be able to
      //generate the location.
      var pIdx = palette.indexOf(blocks.get(vec));
      d.writeByte((byte)pIdx);
    }

    for(var vec : locs) {
      d.writeByte(toBiomeId(biomes.get(vec)));
    }
  }

  //Write strings in a UTF-8 length-prefixed format. I don't like DataOutputStream#writeUTF
  private static void writeStrings(DataOutputStream d, List<String> strings) throws IOException {
    for(var s : strings) {
      var b = s.getBytes(Charset.defaultCharset()); //Should always be UTF-8
      d.writeByte(b.length);
      d.write(b); //Should always be UTF-8
    }
  }

  private static byte toBiomeId(Biome b) {    
    return switch(b) {
      case Biome.BADLANDS -> 0;
      case Biome.BAMBOO_JUNGLE -> 1;
      case Biome.BASALT_DELTAS -> 2;
      case Biome.BEACH -> 3;
      case Biome.BIRCH_FOREST -> 4;
      case Biome.CHERRY_GROVE -> 5;
      case Biome.COLD_OCEAN -> 6;
      case Biome.CRIMSON_FOREST -> 7;
      case Biome.DARK_FOREST -> 8;
      case Biome.DEEP_COLD_OCEAN -> 9;
      case Biome.DEEP_DARK -> 10;
      case Biome.DEEP_FROZEN_OCEAN -> 11;
      case Biome.DEEP_LUKEWARM_OCEAN -> 12;
      case Biome.DEEP_OCEAN -> 13;
      case Biome.DESERT -> 14;
      case Biome.DRIPSTONE_CAVES -> 15;
      case Biome.END_BARRENS -> 16;
      case Biome.END_HIGHLANDS -> 17;
      case Biome.END_MIDLANDS -> 18;
      case Biome.ERODED_BADLANDS -> 19;
      case Biome.FLOWER_FOREST -> 20;
      case Biome.FOREST -> 21;
      case Biome.FROZEN_OCEAN -> 22;
      case Biome.FROZEN_PEAKS -> 23;
      case Biome.FROZEN_RIVER -> 24;
      case Biome.GROVE -> 25;
      case Biome.ICE_SPIKES -> 26;
      case Biome.JAGGED_PEAKS -> 27;
      case Biome.JUNGLE -> 28;
      case Biome.LUKEWARM_OCEAN -> 29;
      case Biome.LUSH_CAVES -> 30;
      case Biome.MANGROVE_SWAMP -> 31;
      case Biome.MEADOW -> 32;
      case Biome.MUSHROOM_FIELDS -> 33;
      case Biome.NETHER_WASTES -> 34;
      case Biome.OCEAN -> 35;
      case Biome.OLD_GROWTH_BIRCH_FOREST -> 36;
      case Biome.OLD_GROWTH_PINE_TAIGA -> 37;
      case Biome.OLD_GROWTH_SPRUCE_TAIGA -> 38;
      case Biome.PLAINS -> 39;
      case Biome.RIVER -> 40;
      case Biome.SAVANNA -> 41;
      case Biome.SAVANNA_PLATEAU -> 42;
      case Biome.SMALL_END_ISLANDS -> 43;
      case Biome.SNOWY_BEACH -> 44;
      case Biome.SNOWY_PLAINS -> 45;
      case Biome.SNOWY_SLOPES -> 46;
      case Biome.SNOWY_TAIGA -> 47;
      case Biome.SOUL_SAND_VALLEY -> 48;
      case Biome.SPARSE_JUNGLE -> 49;
      case Biome.STONY_PEAKS -> 50;
      case Biome.STONY_SHORE -> 51;
      case Biome.SUNFLOWER_PLAINS -> 52;
      case Biome.SWAMP -> 53;
      case Biome.TAIGA -> 54;
      case Biome.THE_END -> 55;
      case Biome.THE_VOID -> 56;
      case Biome.WARM_OCEAN -> 57;
      case Biome.WARPED_FOREST -> 58;
      case Biome.WINDSWEPT_FOREST -> 59;
      case Biome.WINDSWEPT_GRAVELLY_HILLS -> 60;
      case Biome.WINDSWEPT_HILLS -> 61;
      case Biome.WINDSWEPT_SAVANNA -> 62;
      case Biome.WOODED_BADLANDS -> 63;
      
      case Biome.CUSTOM -> toBiomeId(Biome.PLAINS);
      default -> toBiomeId(Biome.PLAINS);
    };
  }
}
