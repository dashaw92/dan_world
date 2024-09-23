package me.danny.danworld;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;

import org.bukkit.Location;

public final class ExtraUtils {

  public static record Coords(double x, double y, double z) {
    public static Coords negateOffset(Selection sel) {
      var zeroX = sel.min().getX();
      var zeroY = sel.min().getY();
      var zeroZ = sel.min().getZ();

      return new Coords(zeroX, zeroY, zeroZ);
    }
  }

  public static byte[] encodeCoords(Selection sel, double bx, double by, double bz, float yaw, float pitch) {
    if(!sel.contains(bx, by, bz)) return null;

    var offset = Coords.negateOffset(sel);
    var x = bx - offset.x();
    var y = by - offset.y();
    var z = bz - offset.z();

    return genOut(d -> {
      d.writeDouble(x);
      d.writeDouble(y);
      d.writeDouble(z);
      d.writeFloat(yaw);
      d.writeFloat(pitch);
    });
  }

  public static byte[] encodeCoords(Selection sel, Location loc) {
    var bx = loc.getX();
    var by = loc.getY();
    var bz = loc.getZ();

    return encodeCoords(sel, bx, by, bz, loc.getYaw(), loc.getPitch());
  }

  public static byte[] encodeString(String str) {
    return genOut(d -> {
      var bytes = str.getBytes();
      d.writeInt(bytes.length);
      d.write(bytes);
    });
  }

  private static byte[] genOut(Throwsumer<DataOutputStream> actions) {
    try(var b = new ByteArrayOutputStream(); var d = new DataOutputStream(b)) {
      actions.throwsume(d);
      return b.toByteArray();
    } catch(Exception _ignored) {
      return null;
    }
  }

  private static interface Throwsumer<T> {
    public void throwsume(T arg) throws Exception;
  }
}
