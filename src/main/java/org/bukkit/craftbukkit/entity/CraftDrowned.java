package org.bukkit.craftbukkit.entity;

import net.minecraft.world.entity.monster.EntityDrowned;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.Drowned;

public class CraftDrowned extends CraftZombie implements Drowned {

    public CraftDrowned(CraftServer server, EntityDrowned entity) {
        super(server, entity);
    }

    @Override
    public EntityDrowned getHandle() {
        return (EntityDrowned) entity;
    }

    @Override
    public String toString() {
        return "CraftDrowned";
    }
}
