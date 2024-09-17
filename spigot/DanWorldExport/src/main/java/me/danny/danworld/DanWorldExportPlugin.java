package me.danny.danworld;

import org.bukkit.plugin.java.JavaPlugin;

public final class DanWorldExportPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getCommand("savedanworld").setExecutor(new DanWorldExportCommand()); 
    }
}
