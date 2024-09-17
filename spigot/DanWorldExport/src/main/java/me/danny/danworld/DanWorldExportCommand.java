package me.danny.danworld;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.regions.Region;

public final class DanWorldExportCommand implements CommandExecutor {

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if(!(sender instanceof Player p)) {
			sender.sendMessage("Only players may run this command.");
			return true;
		}
		
	  if(args.length != 1) {
	  	sender.sendMessage("Must provide world name to save to.");
	  	return true; 
    }

    var sessions = WorldEdit.getInstance().getSessionManager();
    var session = sessions.getIfPresent(BukkitAdapter.adapt(p));

    if(session == null) {
    	p.sendMessage("You don't have anything selected to export.");
    	return true;
    }

		Region region;
    try {
    	region = session.getSelection();
    } catch(Exception _ignored) {
    	p.sendMessage("Your selection is incomplete.");
    	return true;
    }

    p.sendMessage("Attempting to save. Monitor console for progress.");
    if(DanWorld.save(Selection.fromWorldEditRegion(region), toWorldName(args[0]))) {
    	p.sendMessage("Success! World saved to " + toWorldName(args[0]) + " in the plugin's folder.");
    } else {
    	p.sendMessage("Save failed.");
    }
		return true;
	}


	private static String toWorldName(String name) {
		if(name.endsWith(".dan")) return name;
		return name + ".dan";
	}
}
