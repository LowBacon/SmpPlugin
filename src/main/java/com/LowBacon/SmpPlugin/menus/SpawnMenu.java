package com.bx.finnishSmp.menus;

import com.bx.finnishSmp.FinnishSmp;
import com.bx.finnishSmp.managers.SpawnManager;

public class SpawnMenu extends TeleportAreaMenu {

    public SpawnMenu(FinnishSmp plugin) {
        super(
                plugin,
                plugin.getConfigManager().getMenus().getString("SPAWN-MENU.TITLE", "&8ѕᴘᴀᴡɴ ᴀʀᴇᴀѕ"),
                plugin.getConfigManager().getMenus().getInt("SPAWN-MENU.SIZE", 54)
        );
    }

    @Override
    protected SpawnManager.AreaType getAreaType() {
        return SpawnManager.AreaType.SPAWN;
    }

    @Override
    protected String getMenuPath() {
        return "SPAWN-MENU";
    }

    @Override
    protected String getTeleportType() {
        return "SPAWN";
    }

    @Override
    protected String getEmptyTitle() {
        return "&cɴᴏ ѕᴘᴀᴡɴ ᴀʀᴇᴀѕ";
    }

    @Override
    protected String getEmptyLore() {
        return "&7ᴛʜᴇʀᴇ ᴀʀᴇ ɴᴏ ᴠᴀʟɪᴅ ᴄᴜʙᴏɪᴅ-ʙᴀѕᴇᴅ ѕᴘᴀᴡɴ ᴀʀᴇᴀѕ ʏᴇᴛ.";
    }
}
