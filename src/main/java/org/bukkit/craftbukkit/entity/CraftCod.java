package org.bukkit.craftbukkit.entity;

import net.minecraft.world.entity.animal.EntityCod;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.Cod;

public class CraftCod extends CraftFish implements Cod {

    public CraftCod(CraftServer server, EntityCod entity) {
        super(server, entity);
    }

    @Override
    public EntityCod getHandle() {
        return (EntityCod) super.getHandle();
    }

    @Override
    public String toString() {
        return "CraftCod";
    }
}
