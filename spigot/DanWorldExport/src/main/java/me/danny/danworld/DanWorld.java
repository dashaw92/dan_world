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
import java.util.function.Consumer;

import org.bukkit.plugin.java.JavaPlugin;

public final class DanWorld {

  public static boolean save(Selection sel, String name) {
    var l = genLogger(name);
    
    var version = 1;
    var width = Math.abs(sel.min().getChunk().getX() - sel.max().getChunk().getX());
    var depth = Math.abs(sel.min().getChunk().getZ() - sel.max().getChunk().getZ());
    
    l.accept("Region to export is %dx%d (width x depth).".formatted(width, depth));

    // var bitmaskLen = Math.ceilDiv(width * depth, 8);
    // var bitmask = new int[bitmaskLen];

    byte[] bytes;
    try(var b = new ByteArrayOutputStream(); var d = new DataOutputStream(b)) {
      b.write(version);
      b.write(width);
      b.write(depth);
      
      for(int x = sel.min().getChunk().getX(); x < sel.max().getChunk().getX(); x++) {
        for(int z = sel.min().getChunk().getZ(); z < sel.max().getChunk().getZ(); z++) {
          writeChunk(l, d, x, z, sel);
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
    var world = sel.min().getWorld();

    var heights = new short[256];
    var biomes = new short[256];
    
    for(int x = 0; x < 16; x++) {
      for(int z = 0; z < 16; z++) {
        heights[z * 16 + x] = (short) (0xFFFF & world.getHighestBlockYAt(16 * cx + x, 16 * cz +  z));
        biomes[z * 16 + x] = (short) 0;
      }
    }

    l.accept("Writing chunk (%d, %d)...".formatted(cx, cz));
    for(var height : heights) {
      d.write(height);
    }

    for(var biome : biomes) {
      d.write(biome);
    }

    var numSections = Math.ceilDiv(sel.max().getBlockY() - sel.min().getBlockY(), 16);
    d.write(numSections);
    
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

    //Localize block lookups to the current chunk
    var bx = 16 * (sel.min().getChunk().getX() + cx);
    var bz = 16 * (sel.min().getChunk().getZ() + cz);
    //And section Y (chunk sections)
    var by = sel.min().getBlockY() + (sectionY * 16);

    for(int y = 0; y < 16; y++) {
      for(int x = 0; x < 16; x++) {
        for(int z = 0; z < 16; z++) {
          var v = new Vec3(x, y, z);
          var matKey = world.getBlockAt(bx + x, by + y, bz + z).getType().getKey();

          locs.add(v);
          blocks.put(v, matKey.getKey());
          unique.add(matKey.getKey());
        }
      }
    }

    l.accept("Chunk section (%d, y = %d, %d) block data scraped. Serializing.".formatted(cx, sectionY, cz));
    //Generate the palette. Order must be stable.
    var palette = new ArrayList<>(unique);

    d.write(sectionY);
    writeStrings(d, palette);
        
    for(var vec : locs) {
      //Since iteration order is always xz per section, location data isn't needed
      //Write only the palette index of this block. The world loader will be able to
      //generate the location.
      var pIdx = palette.indexOf(blocks.get(vec));
      d.write((byte)pIdx);
    }
  }

  //Write strings in a UTF-8 length-prefixed format. I don't like DataOutputStream#writeUTF
  private static void writeStrings(DataOutputStream d, ArrayList<String> strings) throws IOException {
    for(var s : strings) {
      var b = s.getBytes(Charset.defaultCharset()); //Should always be UTF-8
      d.write(b.length);
      d.write(b); //Should always be UTF-8
    }
  }
}
