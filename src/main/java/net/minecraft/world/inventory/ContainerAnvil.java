package net.minecraft.world.inventory;

import com.mojang.logging.LogUtils;
import java.util.Iterator;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.SystemUtils;
import net.minecraft.network.chat.IChatBaseComponent;
import net.minecraft.tags.TagsBlock;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.entity.player.PlayerInventory;
import net.minecraft.world.item.ItemEnchantedBook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentManager;
import net.minecraft.world.level.block.BlockAnvil;
import net.minecraft.world.level.block.state.IBlockData;
import org.slf4j.Logger;

// CraftBukkit start
import org.bukkit.craftbukkit.inventory.CraftInventoryView;
// CraftBukkit end

public class ContainerAnvil extends ContainerAnvilAbstract {

    public static final int INPUT_SLOT = 0;
    public static final int ADDITIONAL_SLOT = 1;
    public static final int RESULT_SLOT = 2;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean DEBUG_COST = false;
    public static final int MAX_NAME_LENGTH = 50;
    public int repairItemCountCost;
    @Nullable
    public String itemName;
    public final ContainerProperty cost;
    private static final int COST_FAIL = 0;
    private static final int COST_BASE = 1;
    private static final int COST_ADDED_BASE = 1;
    private static final int COST_REPAIR_MATERIAL = 1;
    private static final int COST_REPAIR_SACRIFICE = 2;
    private static final int COST_INCOMPATIBLE_PENALTY = 1;
    private static final int COST_RENAME = 1;
    private static final int INPUT_SLOT_X_PLACEMENT = 27;
    private static final int ADDITIONAL_SLOT_X_PLACEMENT = 76;
    private static final int RESULT_SLOT_X_PLACEMENT = 134;
    private static final int SLOT_Y_PLACEMENT = 47;
    // CraftBukkit start
    public static final int DEFAULT_DENIED_COST = -1;
    public int maximumRepairCost = 40;
    private CraftInventoryView bukkitEntity;
    // CraftBukkit end

    public ContainerAnvil(int i, PlayerInventory playerinventory) {
        this(i, playerinventory, ContainerAccess.NULL);
    }

    public ContainerAnvil(int i, PlayerInventory playerinventory, ContainerAccess containeraccess) {
        super(Containers.ANVIL, i, playerinventory, containeraccess);
        this.cost = ContainerProperty.standalone();
        this.addDataSlot(this.cost);
    }

    @Override
    protected ItemCombinerMenuSlotDefinition createInputSlotDefinitions() {
        return ItemCombinerMenuSlotDefinition.create().withSlot(0, 27, 47, (itemstack) -> {
            return true;
        }).withSlot(1, 76, 47, (itemstack) -> {
            return true;
        }).withResultSlot(2, 134, 47).build();
    }

    @Override
    protected boolean isValidBlock(IBlockData iblockdata) {
        return iblockdata.is(TagsBlock.ANVIL);
    }

    @Override
    protected boolean mayPickup(EntityHuman entityhuman, boolean flag) {
        return (entityhuman.getAbilities().instabuild || entityhuman.experienceLevel >= this.cost.get()) && this.cost.get() > ContainerAnvil.DEFAULT_DENIED_COST && flag; // CraftBukkit - allow cost 0 like a free item
    }

    @Override
    protected void onTake(EntityHuman entityhuman, ItemStack itemstack) {
        if (!entityhuman.getAbilities().instabuild) {
            entityhuman.giveExperienceLevels(-this.cost.get());
        }

        this.inputSlots.setItem(0, ItemStack.EMPTY);
        if (this.repairItemCountCost > 0) {
            ItemStack itemstack1 = this.inputSlots.getItem(1);

            if (!itemstack1.isEmpty() && itemstack1.getCount() > this.repairItemCountCost) {
                itemstack1.shrink(this.repairItemCountCost);
                this.inputSlots.setItem(1, itemstack1);
            } else {
                this.inputSlots.setItem(1, ItemStack.EMPTY);
            }
        } else {
            this.inputSlots.setItem(1, ItemStack.EMPTY);
        }

        this.cost.set(DEFAULT_DENIED_COST); // CraftBukkit - use a variable for set a cost for denied item
        this.access.execute((world, blockposition) -> {
            IBlockData iblockdata = world.getBlockState(blockposition);

            if (!entityhuman.getAbilities().instabuild && iblockdata.is(TagsBlock.ANVIL) && entityhuman.getRandom().nextFloat() < 0.12F) {
                IBlockData iblockdata1 = BlockAnvil.damage(iblockdata);

                if (iblockdata1 == null) {
                    world.removeBlock(blockposition, false);
                    world.levelEvent(1029, blockposition, 0);
                } else {
                    world.setBlock(blockposition, iblockdata1, 2);
                    world.levelEvent(1030, blockposition, 0);
                }
            } else {
                world.levelEvent(1030, blockposition, 0);
            }

        });
    }

    @Override
    public void createResult() {
        ItemStack itemstack = this.inputSlots.getItem(0);

        this.cost.set(1);
        int i = 0;
        byte b0 = 0;
        byte b1 = 0;

        if (itemstack.isEmpty()) {
            org.bukkit.craftbukkit.event.CraftEventFactory.callPrepareAnvilEvent(getBukkitView(), ItemStack.EMPTY); // CraftBukkit
            this.cost.set(DEFAULT_DENIED_COST); // CraftBukkit - use a variable for set a cost for denied item
        } else {
            ItemStack itemstack1 = itemstack.copy();
            ItemStack itemstack2 = this.inputSlots.getItem(1);
            Map<Enchantment, Integer> map = EnchantmentManager.getEnchantments(itemstack1);
            int j = b0 + itemstack.getBaseRepairCost() + (itemstack2.isEmpty() ? 0 : itemstack2.getBaseRepairCost());

            this.repairItemCountCost = 0;
            if (!itemstack2.isEmpty()) {
                boolean flag = itemstack2.is(Items.ENCHANTED_BOOK) && !ItemEnchantedBook.getEnchantments(itemstack2).isEmpty();
                int k;
                int l;
                int i1;

                if (itemstack1.isDamageableItem() && itemstack1.getItem().isValidRepairItem(itemstack, itemstack2)) {
                    k = Math.min(itemstack1.getDamageValue(), itemstack1.getMaxDamage() / 4);
                    if (k <= 0) {
                        org.bukkit.craftbukkit.event.CraftEventFactory.callPrepareAnvilEvent(getBukkitView(), ItemStack.EMPTY); // CraftBukkit
                        this.cost.set(DEFAULT_DENIED_COST); // CraftBukkit - use a variable for set a cost for denied item
                        return;
                    }

                    for (i1 = 0; k > 0 && i1 < itemstack2.getCount(); ++i1) {
                        l = itemstack1.getDamageValue() - k;
                        itemstack1.setDamageValue(l);
                        ++i;
                        k = Math.min(itemstack1.getDamageValue(), itemstack1.getMaxDamage() / 4);
                    }

                    this.repairItemCountCost = i1;
                } else {
                    if (!flag && (!itemstack1.is(itemstack2.getItem()) || !itemstack1.isDamageableItem())) {
                        org.bukkit.craftbukkit.event.CraftEventFactory.callPrepareAnvilEvent(getBukkitView(), ItemStack.EMPTY); // CraftBukkit
                        this.cost.set(DEFAULT_DENIED_COST); // CraftBukkit - use a variable for set a cost for denied item
                        return;
                    }

                    if (itemstack1.isDamageableItem() && !flag) {
                        k = itemstack.getMaxDamage() - itemstack.getDamageValue();
                        i1 = itemstack2.getMaxDamage() - itemstack2.getDamageValue();
                        l = i1 + itemstack1.getMaxDamage() * 12 / 100;
                        int j1 = k + l;
                        int k1 = itemstack1.getMaxDamage() - j1;

                        if (k1 < 0) {
                            k1 = 0;
                        }

                        if (k1 < itemstack1.getDamageValue()) {
                            itemstack1.setDamageValue(k1);
                            i += 2;
                        }
                    }

                    Map<Enchantment, Integer> map1 = EnchantmentManager.getEnchantments(itemstack2);
                    boolean flag1 = false;
                    boolean flag2 = false;
                    Iterator iterator = map1.keySet().iterator();

                    while (iterator.hasNext()) {
                        Enchantment enchantment = (Enchantment) iterator.next();

                        if (enchantment != null) {
                            int l1 = (Integer) map.getOrDefault(enchantment, 0);
                            int i2 = (Integer) map1.get(enchantment);

                            i2 = l1 == i2 ? i2 + 1 : Math.max(i2, l1);
                            boolean flag3 = enchantment.canEnchant(itemstack);

                            if (this.player.getAbilities().instabuild || itemstack.is(Items.ENCHANTED_BOOK)) {
                                flag3 = true;
                            }

                            Iterator iterator1 = map.keySet().iterator();

                            while (iterator1.hasNext()) {
                                Enchantment enchantment1 = (Enchantment) iterator1.next();

                                if (enchantment1 != enchantment && !enchantment.isCompatibleWith(enchantment1)) {
                                    flag3 = false;
                                    ++i;
                                }
                            }

                            if (!flag3) {
                                flag2 = true;
                            } else {
                                flag1 = true;
                                if (i2 > enchantment.getMaxLevel()) {
                                    i2 = enchantment.getMaxLevel();
                                }

                                map.put(enchantment, i2);
                                int j2 = 0;

                                switch (enchantment.getRarity()) {
                                    case COMMON:
                                        j2 = 1;
                                        break;
                                    case UNCOMMON:
                                        j2 = 2;
                                        break;
                                    case RARE:
                                        j2 = 4;
                                        break;
                                    case VERY_RARE:
                                        j2 = 8;
                                }

                                if (flag) {
                                    j2 = Math.max(1, j2 / 2);
                                }

                                i += j2 * i2;
                                if (itemstack.getCount() > 1) {
                                    i = 40;
                                }
                            }
                        }
                    }

                    if (flag2 && !flag1) {
                        org.bukkit.craftbukkit.event.CraftEventFactory.callPrepareAnvilEvent(getBukkitView(), ItemStack.EMPTY); // CraftBukkit
                        this.cost.set(DEFAULT_DENIED_COST); // CraftBukkit - use a variable for set a cost for denied item
                        return;
                    }
                }
            }

            if (this.itemName != null && !SystemUtils.isBlank(this.itemName)) {
                if (!this.itemName.equals(itemstack.getHoverName().getString())) {
                    b1 = 1;
                    i += b1;
                    itemstack1.setHoverName(IChatBaseComponent.literal(this.itemName));
                }
            } else if (itemstack.hasCustomHoverName()) {
                b1 = 1;
                i += b1;
                itemstack1.resetHoverName();
            }

            this.cost.set(j + i);
            if (i <= 0) {
                itemstack1 = ItemStack.EMPTY;
            }

            if (b1 == i && b1 > 0 && this.cost.get() >= maximumRepairCost) { // CraftBukkit
                this.cost.set(maximumRepairCost - 1); // CraftBukkit
            }

            if (this.cost.get() >= maximumRepairCost && !this.player.getAbilities().instabuild) { // CraftBukkit
                itemstack1 = ItemStack.EMPTY;
            }

            if (!itemstack1.isEmpty()) {
                int k2 = itemstack1.getBaseRepairCost();

                if (!itemstack2.isEmpty() && k2 < itemstack2.getBaseRepairCost()) {
                    k2 = itemstack2.getBaseRepairCost();
                }

                if (b1 != i || b1 == 0) {
                    k2 = calculateIncreasedRepairCost(k2);
                }

                itemstack1.setRepairCost(k2);
                EnchantmentManager.setEnchantments(map, itemstack1);
            }

            org.bukkit.craftbukkit.event.CraftEventFactory.callPrepareAnvilEvent(getBukkitView(), itemstack1); // CraftBukkit
            sendAllDataToRemote(); // CraftBukkit - SPIGOT-6686: Always send completed inventory to stay in sync with client
            this.broadcastChanges();
        }
    }

    public static int calculateIncreasedRepairCost(int i) {
        return i * 2 + 1;
    }

    public boolean setItemName(String s) {
        String s1 = validateName(s);

        if (s1 != null && !s1.equals(this.itemName)) {
            this.itemName = s1;
            if (this.getSlot(2).hasItem()) {
                ItemStack itemstack = this.getSlot(2).getItem();

                if (SystemUtils.isBlank(s1)) {
                    itemstack.resetHoverName();
                } else {
                    itemstack.setHoverName(IChatBaseComponent.literal(s1));
                }
            }

            this.createResult();
            return true;
        } else {
            return false;
        }
    }

    @Nullable
    private static String validateName(String s) {
        String s1 = SharedConstants.filterText(s);

        return s1.length() <= 50 ? s1 : null;
    }

    public int getCost() {
        return this.cost.get();
    }

    // CraftBukkit start
    @Override
    public CraftInventoryView getBukkitView() {
        if (bukkitEntity != null) {
            return bukkitEntity;
        }

        org.bukkit.craftbukkit.inventory.CraftInventory inventory = new org.bukkit.craftbukkit.inventory.CraftInventoryAnvil(
                access.getLocation(), this.inputSlots, this.resultSlots, this);
        bukkitEntity = new CraftInventoryView(this.player.getBukkitEntity(), inventory, this);
        return bukkitEntity;
    }
    // CraftBukkit end
}
