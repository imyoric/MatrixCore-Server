package net.minecraft.world.level;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.annotation.Nullable;
import net.minecraft.SystemUtils;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.particles.Particles;
import net.minecraft.server.level.WorldServer;
import net.minecraft.sounds.SoundCategory;
import net.minecraft.sounds.SoundEffects;
import net.minecraft.util.MathHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityLiving;
import net.minecraft.world.entity.item.EntityItem;
import net.minecraft.world.entity.item.EntityTNTPrimed;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.entity.projectile.IProjectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentProtection;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BlockFireAbstract;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.TileEntity;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParameters;
import net.minecraft.world.phys.AxisAlignedBB;
import net.minecraft.world.phys.MovingObjectPosition;
import net.minecraft.world.phys.Vec3D;

// CraftBukkit start
import net.minecraft.world.entity.boss.EntityComplexPart;
import net.minecraft.world.entity.boss.enderdragon.EntityEnderDragon;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.Location;
import org.bukkit.event.block.BlockExplodeEvent;
import ru.yoricya.minecraft.matrixcore.MatrixCore;
// CraftBukkit end

public class Explosion {

    private static final ExplosionDamageCalculator EXPLOSION_DAMAGE_CALCULATOR = new ExplosionDamageCalculator();
    private static final int MAX_DROPS_PER_COMBINED_STACK = 16;
    private final boolean fire;
    private final Explosion.Effect blockInteraction;
    private final RandomSource random = RandomSource.createThreadSafe();
    private final World level;
    private final double x;
    private final double y;
    private final double z;
    @Nullable
    public final Entity source;
    private final float radius;
    private final DamageSource damageSource;
    private final ExplosionDamageCalculator damageCalculator;
    private final CopyOnWriteArrayList<BlockPosition> toBlow;
    private final Map<EntityHuman, Vec3D> hitPlayers;
    public boolean wasCanceled = false; // CraftBukkit - add field

    public Explosion(World world, @Nullable Entity entity, double d0, double d1, double d2, float f, List<BlockPosition> list) {
        this(world, entity, d0, d1, d2, f, false, Explosion.Effect.DESTROY_WITH_DECAY, list);
    }

    public Explosion(World world, @Nullable Entity entity, double d0, double d1, double d2, float f, boolean flag, Explosion.Effect explosion_effect, List<BlockPosition> list) {
        this(world, entity, d0, d1, d2, f, flag, explosion_effect);
        toBlow.addAll(list);
    }

    public Explosion(World world, @Nullable Entity entity, double d0, double d1, double d2, float f, boolean flag, Explosion.Effect explosion_effect) {
        this(world, entity, (DamageSource) null, (ExplosionDamageCalculator) null, d0, d1, d2, f, flag, explosion_effect);
    }

    public Explosion(World world, @Nullable Entity entity, @Nullable DamageSource damagesource, @Nullable ExplosionDamageCalculator explosiondamagecalculator, double d0, double d1, double d2, float f, boolean flag, Explosion.Effect explosion_effect) {
        toBlow = new CopyOnWriteArrayList();
        hitPlayers = Maps.newHashMap();
        level = world;
        source = entity;
        radius = (float) Math.max(f, 0.0); // CraftBukkit - clamp bad values
        x = d0;
        y = d1;
        z = d2;
        fire = flag;
        blockInteraction = explosion_effect;
        damageSource = damagesource == null ? world.damageSources().explosion(this) : damagesource;
        damageCalculator = explosiondamagecalculator == null ? makeDamageCalculator(entity) : explosiondamagecalculator;
    }

    private ExplosionDamageCalculator makeDamageCalculator(@Nullable Entity entity) {
        return (ExplosionDamageCalculator) (entity == null ? Explosion.EXPLOSION_DAMAGE_CALCULATOR : new ExplosionDamageCalculatorEntity(entity));
    }

    public static float getSeenPercent(Vec3D vec3d, Entity entity) {
        AxisAlignedBB axisalignedbb = entity.getBoundingBox();
        double d0 = 1.0D / ((axisalignedbb.maxX - axisalignedbb.minX) * 2.0D + 1.0D);
        double d1 = 1.0D / ((axisalignedbb.maxY - axisalignedbb.minY) * 2.0D + 1.0D);
        double d2 = 1.0D / ((axisalignedbb.maxZ - axisalignedbb.minZ) * 2.0D + 1.0D);
        double d3 = (1.0D - Math.floor(1.0D / d0) * d0) / 2.0D;
        double d4 = (1.0D - Math.floor(1.0D / d2) * d2) / 2.0D;

        if (d0 >= 0.0D && d1 >= 0.0D && d2 >= 0.0D) {
            int i = 0;
            int j = 0;

            for (double d5 = 0.0D; d5 <= 1.0D; d5 += d0) {
                for (double d6 = 0.0D; d6 <= 1.0D; d6 += d1) {
                    for (double d7 = 0.0D; d7 <= 1.0D; d7 += d2) {
                        double d8 = MathHelper.lerp(d5, axisalignedbb.minX, axisalignedbb.maxX);
                        double d9 = MathHelper.lerp(d6, axisalignedbb.minY, axisalignedbb.maxY);
                        double d10 = MathHelper.lerp(d7, axisalignedbb.minZ, axisalignedbb.maxZ);
                        Vec3D vec3d1 = new Vec3D(d8 + d3, d9, d10 + d4);

                        if (entity.level().clip(new RayTrace(vec3d1, vec3d, RayTrace.BlockCollisionOption.COLLIDER, RayTrace.FluidCollisionOption.NONE, entity)).getType() == MovingObjectPosition.EnumMovingObjectType.MISS) {
                            ++i;
                        }

                        ++j;
                    }
                }
            }

            return (float) i / (float) j;
        } else {
            return 0.0F;
        }
    }
    Explosion getThis(){return this;}

    public void explode() { //Edited by MatrixCore
        if(source == null) return;
        if (radius < 0.1F && !source.isExploded) return;
        Location loc = new Location(level.getWorld(), x,y,z);
        MatrixCore.MatrixAsyncTask task = (MatrixCore.MatrixAsyncTask) Bukkit.getScheduler().runAsyncTaskWithMatrix(new Runnable() {
            @Override
            public void run() {
                Set<BlockPosition> set = Sets.newHashSet();
                List<org.bukkit.block.Block> blocks = level.getNearbyBlocks(loc, (int) (radius + (level.threadSafeRandom.nextFloat()/1.2)));
                for(org.bukkit.block.Block block : blocks){
                    BlockPosition blockposition = BlockPosition.containing(block.getX(), block.getY(), block.getZ());
                    if (!level.isInWorldBounds(blockposition)) break;

                    IBlockData iblockdata = level.getBlockState(blockposition);
                    Fluid fluid = level.getFluidState(blockposition);

                    float f = (radius + (level.threadSafeRandom.nextFloat()/1.2f));

                    Optional<Float> optional = damageCalculator.getBlockExplosionResistance(getThis(), level, blockposition, iblockdata, fluid);

                    if (optional.isPresent()) f -= ((Float) optional.get() + 0.3F) * 0.3F;
                    if (f > 0.0F && damageCalculator.shouldBlockExplode(getThis(), level, blockposition, iblockdata, f)) set.add(blockposition);
                }
                toBlow.addAll(set);
            }
        });

        source.isExploded = true;
        Vec3D vec3d = new Vec3D(x, y, z);
        level.gameEvent(source, GameEvent.EXPLODE, vec3d);
        Collection<org.bukkit.entity.Entity> ents = level.getWorld().getNearbyEntities(loc, 8, 8, 8);

        for(org.bukkit.entity.Entity ent : ents){
            Location locEntity = ent.getLocation();
            net.minecraft.world.entity.Entity mEnt = ((CraftEntity) ent).getHandle();

            if (mEnt instanceof EntityEnderDragon) {
                float f2 = radius * 2.0F;
                for (EntityComplexPart entityComplexPart : ((EntityEnderDragon) mEnt).subEntities) {
                    // Calculate damage separately for each EntityComplexPart
                    double d7part;
                    if (ents.contains(entityComplexPart) && (d7part = Math.sqrt(entityComplexPart.distanceToSqr(vec3d)) / f2) <= 1.0D) {
                        double d13part = (1.0D - d7part) * getSeenPercent(vec3d, entityComplexPart);
                        entityComplexPart.hurt(getDamageSource(), (float) ((int) ((d13part * d13part + d13part) / 2.0D * 7.0D * (double) f2 + 1.0D)));
                    }
                }
            } else {
                List<org.bukkit.block.Block> blocksInPath = level.getBlocksOnPath(loc, ent.getLocation());
                float damage = 9.5f - (((float) loc.distance(locEntity)));
                for(org.bukkit.block.Block bl : blocksInPath) damage -= bl.getType().getHardness() / 1.1f;

                if(damage > 2) {
                    float finalDamage = damage;
                    Bukkit.getScheduler().runTaskWithMatrix(new Runnable() {
                        @Override
                        public void run() {
                            mEnt.hurt(getDamageSource(), finalDamage);
                        }
                    });
                }
            }
        }
        task.RunnedCheck();
    }

    public static <T> void OBJUTILshuffle(CopyOnWriteArrayList<T> var0, RandomSource var1) {
        int var2 = var0.size();

        for(int var3 = var2; var3 > 1; --var3) {
            int var4 = var1.nextInt(var3);
            var0.set(var3 - 1, var0.set(var4, var0.get(var3 - 1)));
        }

    }

    public void finalizeExplosion(boolean flag) {
        if (level.isClientSide) Bukkit.getScheduler().runTaskWithMatrix(new Runnable() {
                @Override
                public void run() {
                    level.playLocalSound(x, y, z, SoundEffects.GENERIC_EXPLODE, SoundCategory.BLOCKS, 4.0F, (1.0F + (level.threadSafeRandom.nextFloat() - level.threadSafeRandom.nextFloat()) * 0.2F) * 0.7F, false);
                }
            },true);


        boolean flag1 = interactsWithBlocks();

        if (flag) {
            if (radius >= 2.0F && flag1) {
                level.addParticle(Particles.EXPLOSION_EMITTER, x, y, z, 1.0D, 0.0D, 0.0D);
            } else {
                level.addParticle(Particles.EXPLOSION, x, y, z, 1.0D, 0.0D, 0.0D);
            }
        }

        if (fire) Bukkit.getScheduler().runAsyncTaskWithMatrix(new Runnable() {
            @Override
            public void run() {
                for (BlockPosition blockposition2 : toBlow) {
                    if (random.nextInt(3) == 0 && level.getBlockState(blockposition2).isAir() && level.getBlockState(blockposition2.below()).isSolidRender(level, blockposition2.below())) {
                        if (!CraftEventFactory.callBlockIgniteEvent(level, blockposition2.getX(), blockposition2.getY(), blockposition2.getZ(), getThis()).isCancelled())
                            Bukkit.getScheduler().runTaskWithMatrix(new Runnable() {
                                @Override
                                public void run() {
                                    level.setBlockAndUpdate(blockposition2, BlockFireAbstract.getState(level, blockposition2));
                                }
                            });
                    }
                }
            }
        });

        if (flag1) {

            ObjectArrayList<Pair<ItemStack, BlockPosition>> objectarraylist = new ObjectArrayList();
            boolean flag2 = getIndirectSourceEntity() instanceof EntityHuman;

            OBJUTILshuffle(toBlow, level.threadSafeRandom);
            // CraftBukkit start
            Bukkit.getScheduler().runTaskWithMatrix(new Runnable() {
                @Override
                public void run() {
                    org.bukkit.World bworld = level.getWorld();
                    org.bukkit.entity.Entity explode = source == null ? null : source.getBukkitEntity();

                    Bukkit.getScheduler().runAsyncTaskWithMatrix(new Runnable() {
                        @Override
                        public void run() {
                            Location location = new Location(bworld, x, y, z);

                            List<org.bukkit.block.Block> blockList = new ObjectArrayList<>();
                            for (int i1 = toBlow.size() - 1; i1 >= 0; i1--) {
                                BlockPosition cpos = toBlow.get(i1);
                                if(cpos == null) break;
                                org.bukkit.block.Block bblock = bworld.getBlockAt(cpos.getX(), cpos.getY(), cpos.getZ());
                                if (!bblock.getType().isAir()) {
                                    blockList.add(bblock);
                                }
                            }

                            boolean cancelled;
                            List<org.bukkit.block.Block> bukkitBlocks;
                            float yield;

                            if (explode != null) {
                                EntityExplodeEvent event = new EntityExplodeEvent(explode, location, blockList, blockInteraction == Explosion.Effect.DESTROY_WITH_DECAY ? 1.0F / radius : 1.0F);
                                level.getCraftServer().getPluginManager().callEvent(event);
                                cancelled = event.isCancelled();
                                bukkitBlocks = event.blockList();
                                yield = event.getYield();
                            } else {
                                BlockExplodeEvent event = new BlockExplodeEvent(location.getBlock(), blockList, blockInteraction == Explosion.Effect.DESTROY_WITH_DECAY ? 1.0F / radius : 1.0F);
                                level.getCraftServer().getPluginManager().callEvent(event);
                                cancelled = event.isCancelled();
                                bukkitBlocks = event.blockList();
                                yield = event.getYield();
                            }

                            toBlow.clear();

                            if (cancelled) {
                                wasCanceled = true;
                                return;
                            }

                            for (org.bukkit.block.Block bblock : bukkitBlocks) {
                                BlockPosition coords = new BlockPosition(bblock.getX(), bblock.getY(), bblock.getZ());
                                toBlow.add(coords);
                            }
                            // CraftBukkit end
                            Bukkit.getScheduler().runAsyncTaskWithMatrix(new Runnable() {
                                @Override
                                public void run() {
                                    for (BlockPosition blockposition : toBlow) {
                                        IBlockData iblockdata = level.getBlockState(blockposition);
                                        Block block = iblockdata.getBlock();
                                        // CraftBukkit start - TNTPrimeEvent
                                        if (block instanceof net.minecraft.world.level.block.BlockTNT) {
                                            Entity sourceEntity = source;
                                            BlockPosition sourceBlock = sourceEntity == null ? BlockPosition.containing(x, y, z) : null;
                                            if (!CraftEventFactory.callTNTPrimeEvent(level, blockposition, org.bukkit.event.block.TNTPrimeEvent.PrimeCause.EXPLOSION, sourceEntity, sourceBlock)) {
                                                level.sendBlockUpdated(blockposition, Blocks.AIR.defaultBlockState(), iblockdata, 3); // Update the block on the client
                                                continue;
                                            }
                                        }
                                        // CraftBukkit end

                                        if (!iblockdata.isAir()) {
                                            Bukkit.getScheduler().runTaskWithMatrix(new Runnable() {
                                                @Override
                                                public void run() {
                                                    BlockPosition blockposition1 = blockposition.immutable();

                                                    level.getProfiler().push("explosion_blocks");
                                                    if (block.dropFromExplosion(getThis())) {
                                                        World world = level;

                                                        if (world instanceof WorldServer) {
                                                            WorldServer worldserver = (WorldServer) world;
                                                            TileEntity tileentity = iblockdata.hasBlockEntity() ? level.getBlockEntity(blockposition) : null;
                                                            LootParams.a lootparams_a = (new LootParams.a(worldserver)).withParameter(LootContextParameters.ORIGIN, Vec3D.atCenterOf(blockposition)).withParameter(LootContextParameters.TOOL, ItemStack.EMPTY).withOptionalParameter(LootContextParameters.BLOCK_ENTITY, tileentity).withOptionalParameter(LootContextParameters.THIS_ENTITY, source);

                                                            if (yield < 1.0F) { // CraftBukkit - add yield
                                                                lootparams_a.withParameter(LootContextParameters.EXPLOSION_RADIUS, 1.0F / yield); // CraftBukkit - add yield
                                                            }

                                                            iblockdata.spawnAfterBreak(worldserver, blockposition, ItemStack.EMPTY, flag2);
                                                            iblockdata.getDrops(lootparams_a).forEach((itemstack) -> {
                                                                addBlockDrops(objectarraylist, itemstack, blockposition1);
                                                            });
                                                        }
                                                    }

                                                    level.setBlock(blockposition, Blocks.AIR.defaultBlockState(), 3);
                                                    block.wasExploded(level, blockposition, getThis());
                                                    level.getProfiler().pop();
                                                }
                                            });
                                        }
                                    }
                                }
                            });

                            ObjectListIterator<Pair<ItemStack, BlockPosition>> objectlistiterator1 = objectarraylist.iterator();
                            while (objectlistiterator1.hasNext()) {
                                Pair<ItemStack, BlockPosition> pair = (Pair) objectlistiterator1.next();
                                Bukkit.getScheduler().runTaskWithMatrix(new Runnable() {
                                    @Override
                                    public void run() {
                                        Block.popResource(level, (BlockPosition) pair.getSecond(), (ItemStack) pair.getFirst());
                                    }
                                });
                            }
                        }
                    });
                }
            }, true);

        }

    }

    public boolean interactsWithBlocks() {
        return blockInteraction != Explosion.Effect.KEEP;
    }

    private static void addBlockDrops(ObjectArrayList<Pair<ItemStack, BlockPosition>> objectarraylist, ItemStack itemstack, BlockPosition blockposition) {
        if (itemstack.isEmpty()) return; // CraftBukkit - SPIGOT-5425
        int i = objectarraylist.size();

        for (int j = 0; j < i; ++j) {
            Pair<ItemStack, BlockPosition> pair = (Pair) objectarraylist.get(j);
            ItemStack itemstack1 = (ItemStack) pair.getFirst();

            if (EntityItem.areMergable(itemstack1, itemstack)) {
                ItemStack itemstack2 = EntityItem.merge(itemstack1, itemstack, 16);

                objectarraylist.set(j, Pair.of(itemstack2, (BlockPosition) pair.getSecond()));
                if (itemstack.isEmpty()) {
                    return;
                }
            }
        }

        objectarraylist.add(Pair.of(itemstack, blockposition));
    }

    public DamageSource getDamageSource() {
        return damageSource;
    }

    public Map<EntityHuman, Vec3D> getHitPlayers() {
        return hitPlayers;
    }

    @Nullable
    public EntityLiving getIndirectSourceEntity() {
        if (source == null) {
            return null;
        } else {
            Entity entity = source;

            if (entity instanceof EntityTNTPrimed) {
                EntityTNTPrimed entitytntprimed = (EntityTNTPrimed) entity;

                return entitytntprimed.getOwner();
            } else {
                entity = source;
                if (entity instanceof EntityLiving) {
                    EntityLiving entityliving = (EntityLiving) entity;

                    return entityliving;
                } else {
                    entity = source;
                    if (entity instanceof IProjectile) {
                        IProjectile iprojectile = (IProjectile) entity;

                        entity = iprojectile.getOwner();
                        if (entity instanceof EntityLiving) {
                            EntityLiving entityliving1 = (EntityLiving) entity;

                            return entityliving1;
                        }
                    }

                    return null;
                }
            }
        }
    }

    @Nullable
    public Entity getDirectSourceEntity() {
        return source;
    }

    public void clearToBlow() {
        toBlow.clear();
    }

    public List<BlockPosition> getToBlow() {
        return toBlow;
    }

    public static enum Effect {

        KEEP, DESTROY, DESTROY_WITH_DECAY;

        private Effect() {}
    }
}
