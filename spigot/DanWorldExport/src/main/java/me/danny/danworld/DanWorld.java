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

import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.Openable;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.Rail;
import org.bukkit.block.data.Rotatable;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.type.Snow;
import org.bukkit.block.data.type.Stairs;
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
    d.writeShort(cx / 16);
    d.writeShort(cz / 16);
    
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

    var data = new HashMap<Vec3, List<Short>>();

    //Localize block lookups to the current chunk
    var baseX = sel.min().getBlockX() + cx;
    var baseZ = sel.min().getBlockZ() + cz;
    //And section Y (chunk sections)
    var baseY = sel.min().getBlockY() + (sectionY * 16);

    for(int y = 0; y < 16; y++) {
      for(int x = 0; x < 16; x++) {
        for(int z = 0; z < 16; z++) {
          var v = new Vec3(x, y, z);
          var block = world.getBlockAt(baseX + x, baseY + y, baseZ + z);
          //minecraft:grass_block
          //          ^^^^^^^^^^^
          var matKey = block.getType().getKey().getKey();
          var biome = world.getBiome(block.getLocation());

          //If the current block is outside of the bounds of the selection, rather than skip it completely,
          //encode it as an empty block.
          //NOTE: Once I get around to doing block data, probably keep a flag so I don't set data for these blocks.
          if(baseX + x > sel.max().getBlockX() || baseZ + z > sel.max().getBlockZ() || baseY + y > sel.max().getBlockY()) {
            matKey = Material.VOID_AIR.getKey().getKey();
            biome = Biome.PLAINS;
          }
          

          locs.add(v);
          blocks.put(v, matKey);
          biomes.put(v, biome);
          unique.add(matKey);

          var blockData = encodeBlockData(block);
          if(!blockData.isEmpty()) {
            data.put(v, blockData);
          }
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

    l.accept("Saving biomes.");
    for(var vec : locs) {
      d.writeByte(toBiomeId(biomes.get(vec)));
    }

    l.accept("Saving block data from %d blocks.".formatted(data.size()));
    d.writeShort((short) data.size());
    for(var vec : data.keySet()) {
      var vals = data.get(vec);

      short blockDataBitfield = (short) (vec.x() << 12 | vec.y() << 8 | vec.z() << 4 | vals.size());
      
      d.writeShort(blockDataBitfield);
      for(var bitfield : vals) {
        d.writeShort(bitfield);
      }
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

  private static short encode(int type, int data) {
    return (short) (type << 12 | data);
  }
  
  private static List<Short> encodeBlockData(Block block) {
    var data = new ArrayList<Short>();
    var bd = block.getBlockData();
    
    if(bd instanceof Orientable o) {
      var type = 0b0000;
      var bits = switch(o.getAxis()) {
        case Axis.X -> 0;
        case Axis.Y -> 1;
        case Axis.Z -> 2;
      };

      data.add(encode(type, bits));
    }

    if(bd instanceof Ageable a) {
      var type = 0b0001;
      var bits = a.getAge();  
      data.add(encode(type, bits));
    }

    if(bd instanceof Snow snow) {
      var type = 0b0010;
      var bits = snow.getLayers();
      data.add(encode(type, bits));
    }

    if(bd instanceof Levelled le) {
      var type = 0b0011;
      var bits = le.getLevel();
      data.add(encode(type, bits));
    }

    if(bd instanceof Bisected bisect) {
      var type = 0b0100;
      var bits = switch(bisect.getHalf()) {
        case Bisected.Half.TOP -> 0;
        case Bisected.Half.BOTTOM -> 1;
      };
      data.add(encode(type, bits));
      
    }

    if(bd instanceof Directional dir) {
      var type = 0b0101;
      var bits = encodeDirection(dir.getFacing());
      data.add(encode(type, bits));
    }

    if(bd instanceof Waterlogged w) {
      var type = 0b0110;
      if(w.isWaterLogged()) {
        data.add(encode(type, 1));
      }
    }

    if(bd instanceof Rotatable r) {
      var type = 0b0111;
      var bits = encodeDirection(r.getRotation());
      data.add(encode(type, bits));
    }

    if(bd instanceof MultipleFacing mf) {
      var type = 0b1000;
      final int NORTH = 1;
      final int SOUTH = 2;
      final int EAST = 4;
      final int WEST = 8;
      final int UP = 16;
      final int DOWN = 32;

      int bits = 0;
      for(var face : mf.getFaces()) {
        switch(face) {
          case BlockFace.NORTH -> bits |= NORTH;
          case BlockFace.SOUTH -> bits |= SOUTH;
          case BlockFace.EAST -> bits |= EAST;
          case BlockFace.WEST -> bits |= WEST;
          case BlockFace.UP -> bits |= UP;
          case BlockFace.DOWN -> bits |= DOWN;
          default -> {}
        }
      }
      data.add(encode(type, bits));
    }

    if(bd instanceof Openable o) {
      var type = 0b1001;
      if(o.isOpen()) {
        data.add(encode(type, 1));
      }
    }

    if(bd instanceof Rail rail) {
      var type = 0b1010;
      var bits = switch(rail.getShape()) {
      		case ASCENDING_EAST -> 1;
      		case ASCENDING_NORTH -> 2;
      		case ASCENDING_SOUTH -> 3;
      		case ASCENDING_WEST -> 4;
      		case EAST_WEST -> 5;
      		case NORTH_EAST -> 6;
      		case NORTH_SOUTH -> 7;
      		case NORTH_WEST -> 8;
      		case SOUTH_EAST -> 9;
      		case SOUTH_WEST -> 10;
      		default -> 5 /* Default to EAST_WEST */;
      };
      data.add(encode(type, bits));
    }

    if(bd instanceof Stairs stairs) {
      var type = 0b1011;
      var bits = switch(stairs.getShape()) {
      		case INNER_LEFT -> 0;
      		case INNER_RIGHT -> 1;
      		case OUTER_LEFT -> 2;
      		case OUTER_RIGHT -> 3;
      		case STRAIGHT -> 4;
      		default -> 4 /* Default to STRAIGHT */;
      };
      data.add(encode(type, bits));
    }
    
    return data;
  }

  private static int encodeDirection(BlockFace facing) {
    return switch(facing) {
    		case BlockFace.DOWN -> 0;
    		case BlockFace.EAST -> 1;
    		case BlockFace.EAST_NORTH_EAST -> 2;
    		case BlockFace.EAST_SOUTH_EAST -> 3;
    		case BlockFace.NORTH -> 4;
    		case BlockFace.NORTH_EAST -> 5;
    		case BlockFace.NORTH_NORTH_EAST -> 6;
    		case BlockFace.NORTH_NORTH_WEST -> 7;
    		case BlockFace.NORTH_WEST -> 8;
    		case BlockFace.SOUTH -> 9;
    		case BlockFace.SOUTH_EAST -> 10;
    		case BlockFace.SOUTH_SOUTH_EAST -> 11;
    		case BlockFace.SOUTH_SOUTH_WEST -> 12;
    		case BlockFace.SOUTH_WEST -> 13;
    		case BlockFace.UP -> 14;
    		case BlockFace.WEST -> 15;
    		case BlockFace.WEST_NORTH_WEST -> 16;
    		case BlockFace.WEST_SOUTH_WEST -> 17;
    		default -> 4 /* Default to NORTH */;
        
      };
  }
}
