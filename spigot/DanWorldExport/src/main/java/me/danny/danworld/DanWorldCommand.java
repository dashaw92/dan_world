package me.danny.danworld;

import java.io.File;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.regions.Region;

import net.md_5.bungee.api.ChatColor;

public final class DanWorldCommand implements CommandExecutor {

	private static final Map<String, DanWorld> loaded = new HashMap<>();
	private static final Map<UUID, String> active = new HashMap<>();
	private static final Set<String> modified = new HashSet<>();
	
	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if(!(sender instanceof Player p)) {
			sender.sendMessage("Only players may run this command.");
			return true;
		}
		
	  if(args.length < 1) {
	  	sender.sendMessage(ChatColor.LIGHT_PURPLE + "Modal:");
	  	sender.sendMessage("load <world> - Set your active world");
	  	sender.sendMessage("status - Display active world");
	  	sender.sendMessage("new <name> - Start a new world, must have a WE selection");
	  	sender.sendMessage("save - Export active world to .dan");
	  	sender.sendMessage("rd - Redefine the active world's bounds");
	  	sender.sendMessage("set - Define an extra in the world");
	  	sender.sendMessage(ChatColor.LIGHT_PURPLE + "Static:");
	  	sender.sendMessage("list - List available worlds");
	  	
	  	return true; 
    }

		var cmdArgs = Arrays.copyOfRange(args, 1, args.length);
		switch(args[0].toLowerCase()) {
			case "rd" -> redef(p);
			case "save" -> save(p);
			case "status" -> status(p);
			case "load" -> load(p, cmdArgs);
			case "new" -> newWorld(p, cmdArgs);
			case "list" -> listWorlds(p);
			case "set" -> setExtra(p, cmdArgs);
			default -> p.sendMessage("Unknown subcommand.");
		}
		
		return true;
	}

	private void setExtra(Player p, String[] args) {
		var world = getWorld(p);
		if(world == null) {
			p.sendMessage("You don't have an active DanWorld.");
			return;
		}
		
		if(args.length < 3) {
			p.sendMessage("Usage: set pos <key> x y z yaw pitch");
			p.sendMessage("Usage: set str <key> <string...>");
			return;
		}

		String key = args[1];
		byte[] value;
		switch(args[0].toLowerCase()) {
			case "pos" -> {
				if(args.length != 7) {
					p.sendMessage("Usage: set pos <key> x y z yaw pitch");
					return;
				}

				double x = Double.parseDouble(args[2]);
				double y = Double.parseDouble(args[3]);
				double z = Double.parseDouble(args[4]);
				value = ExtraUtils.encodeCoords(world.getSelection(), x, y, z, p.getLocation().getYaw(), p.getLocation().getPitch());
			}
			case "str" -> {
				value = ExtraUtils.encodeString(String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
			}
			default -> {
				p.sendMessage("Unknown extra type. Only pos and str are supported.");
				return;
			}
		}

		world.setExtra(key, value);
		p.sendMessage("%s -> %s".formatted(key, Arrays.toString(value)));
	}

	private void newWorld(Player p, String[] args) {
		if(args.length != 1) {
			p.sendMessage("Usage: new <name>");
			return;
		}
		
    var sessions = WorldEdit.getInstance().getSessionManager();
    var session = sessions.getIfPresent(BukkitAdapter.adapt(p));

    if(session == null) {
    	p.sendMessage("You don't have anything selected.");
    	return;
    }

		Region region;
    try {
    	region = session.getSelection();
    } catch(Exception _ignored) {
    	p.sendMessage("Your selection is incomplete.");
    	return;
    }

		var world = new DanWorld();
		world.setName(args[0]);
    world.setSelection(Selection.fromWorldEditRegion(region));
    modified.add(world.getName());
		active.put(p.getUniqueId(), world.getName());
		loaded.put(world.getName(), world);
	}
	
	private void listWorlds(Player p) {
		var folder = getWorldFolder();

		var output = new ArrayList<String>();
		for(var maybeWorld : folder.list()) {
			if(!maybeWorld.endsWith(".dan.yml")) continue;

			var splitAt = maybeWorld.indexOf(".yml");
			String name = maybeWorld.substring(0, splitAt);
			output.add("%s%s".formatted(loaded.containsKey(name) ? "@" : "", name));
		}
		
		p.sendMessage(String.join(" ", output));
	}
	
	private void load(Player p, String[] args) {
		if(args.length != 1) {
			p.sendMessage("Usage: load <world>");
			return;
		}

		String name = args[0];
		if(!name.endsWith(".dan")) name += ".dan";
		
		var worldFile = getWorldFile(name);
		var maybeWorld = DanWorld.loadFromFile(worldFile);
		if(maybeWorld.isEmpty()) {
			p.sendMessage("World not found.");
			return;
		}

		var world = maybeWorld.get();
		modified.remove(world.getName());
		loaded.put(world.getName(), world);
		active.put(p.getUniqueId(), world.getName());
		p.sendMessage("Active world switched to " + world.getName());
	}

	private void status(Player p) {
		var world = getWorld(p);
		if(world == null) {
			p.sendMessage("You don't have an active DanWorld.");

			return;
		}

		var m = modified.contains(world.getName());
		p.sendMessage("World %s[%s] dimension %s".formatted(m ? "*" : "", world.getName(), world.getDimension()));
	}

	private void redef(Player p) {
		var world = getWorld(p);
		if(world == null) {
			p.sendMessage("You don't have an active DanWorld.");
			return;
		}
		
    var sessions = WorldEdit.getInstance().getSessionManager();
    var session = sessions.getIfPresent(BukkitAdapter.adapt(p));

    if(session == null) {
    	p.sendMessage("You don't have anything selected.");
    	return;
    }

		Region region;
    try {
    	region = session.getSelection();
    } catch(Exception _ignored) {
    	p.sendMessage("Your selection is incomplete.");
    	return;
    }

    world.setSelection(Selection.fromWorldEditRegion(region));
    modified.add(world.getName());
    p.sendMessage("World redefined");
	}

	private void save(Player p) {
		var world = getWorld(p);
		if(world == null) {
			p.sendMessage("You don't have an active DanWorld.");
			return;
		}
		
    p.sendMessage("Attempting to save. Monitor console for progress.");

    DanWorld.saveToFile(world, new File(getWorldFolder(), world.getName() + ".yml"));
    if(world.exportWorld()) {
    	p.sendMessage("Success! World saved to " + world.getName() + ".dan in the plugin's folder.");
    	modified.remove(world.getName());
    } else {
    	p.sendMessage("Save failed.");
    }
		
	}

	private static DanWorld getWorld(Player p) {
		if(!active.containsKey(p.getUniqueId())) return null;
		return loaded.get(active.get(p.getUniqueId()));
	}

	private static File getWorldFolder() {
		return JavaPlugin.getPlugin(DanWorldExportPlugin.class).getDataFolder();
	}

	private static File getWorldFile(String name) {
		return new File(getWorldFolder(), name + ".yml");
	}
}
