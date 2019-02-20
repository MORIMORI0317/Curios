package top.theillusivec4.curios.common.capability;

import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Maps;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.INBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fml.network.NetworkDirection;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;
import top.theillusivec4.curios.api.CurioType;
import top.theillusivec4.curios.api.CuriosAPI;
import top.theillusivec4.curios.api.CuriosCapability;
import top.theillusivec4.curios.api.ICurioItemHandler;
import top.theillusivec4.curios.common.network.NetworkHandler;
import top.theillusivec4.curios.common.network.server.sync.SPacketSyncActive;
import top.theillusivec4.curios.common.network.server.sync.SPacketSyncSize;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.SortedMap;

public class CapCurioInventory {

    public static void register() {
        CapabilityManager.INSTANCE.register(ICurioItemHandler.class, new Capability.IStorage<ICurioItemHandler>() {
            @Override
            public INBTBase writeNBT(Capability<ICurioItemHandler> capability, ICurioItemHandler instance,
                                     EnumFacing side) {
                ImmutableSortedMap<String, ItemStackHandler> curioMap = instance.getCurioMap();
                NBTTagCompound compound = new NBTTagCompound();
                NBTTagList taglist = new NBTTagList();

                for (String identifier : curioMap.keySet()) {
                    ItemStackHandler stackHandler = curioMap.get(identifier);
                    NBTTagCompound itemtag = stackHandler.serializeNBT();
                    itemtag.setString("Identifier", identifier);
                    taglist.add(itemtag);
                }
                compound.setTag("Curios", taglist);
                return compound;
            }

            @Override
            public void readNBT(Capability<ICurioItemHandler> capability, ICurioItemHandler instance, EnumFacing side, INBTBase nbt) {
                NBTTagList tagList = ((NBTTagCompound)nbt).getList("Curios", Constants.NBT.TAG_COMPOUND);
                SortedMap<String, ItemStackHandler> curios = Maps.newTreeMap();

                if (!tagList.isEmpty()) {

                    for (int i = 0; i < tagList.size(); i++) {
                        NBTTagCompound itemtag = tagList.getCompound(i);
                        String identifier = itemtag.getString("Identifier");
                        CurioType type = CuriosAPI.getType(identifier);

                        if (type != null) {
                            ItemStackHandler stackHandler = instance.getStackHandler(identifier);

                            if (stackHandler != null) {
                                NBTTagList nbttaglist = itemtag.getList("Items", 10);
                                NonNullList<ItemStack> stacks = NonNullList.withSize(nbttaglist.size(), ItemStack.EMPTY);

                                for(int j = 0; j < nbttaglist.size(); j++) {
                                    NBTTagCompound nbttagcompound = nbttaglist.getCompound(j);
                                    int l = nbttagcompound.getByte("Slot") & 255;
                                    if (l < stacks.size()) {
                                        stacks.set(j, ItemStack.read(nbttagcompound));
                                    }
                                }
                                curios.put(identifier, new ItemStackHandler(stacks));
                            }
                        }
                    }
                    instance.setCurioMap(curios);
                }
            }
        }, CurioInventoryWrapper::new);
    }

    public static ICapabilityProvider createProvider(final EntityLivingBase livingBase) {
        return new Provider(livingBase);
    }

    public static class CurioInventoryWrapper implements ICurioItemHandler {

        SortedMap<String, ItemStackHandler> curioSlots;
        SortedMap<String, ItemStackHandler> prevCurioSlots;
        EntityLivingBase wearer;

        CurioInventoryWrapper() {
            this(null);
        }

        CurioInventoryWrapper(final EntityLivingBase livingBase) {
            this.curioSlots = this.initCurioSlots();
            this.prevCurioSlots = this.initCurioSlots();
            this.wearer = livingBase;
        }

        @Override
        public void setStackInSlot(String identifier, int slot, @Nonnull ItemStack stack) {
            this.curioSlots.get(identifier).setStackInSlot(slot, stack);
        }

        private SortedMap<String, ItemStackHandler> initCurioSlots() {
            SortedMap<String, ItemStackHandler> slots = Maps.newTreeMap();
            Map<String, CurioType> registry = CuriosAPI.getTypeRegistry();

            for (String id : registry.keySet()) {
                CurioType entry = registry.get(id);

                if (entry.isEnabled()) {
                    slots.put(id, new ItemStackHandler(entry.getSize()));
                }
            }
            return slots;
        }

        @Override
        public int getSlots() {
            int totalSlots = 0;

            for (ItemStackHandler stacks : curioSlots.values()) {
                totalSlots += stacks.getSlots();
            }
            return totalSlots;
        }

        @Nonnull
        public ItemStack getStackInSlot(String identifier, int slot) {
            return this.curioSlots.get(identifier).getStackInSlot(slot);
        }

        @Nullable
        @Override
        public ItemStackHandler getStackHandler(String identifier) {
            return this.curioSlots.get(identifier);
        }

        @Override
        public ImmutableSortedMap<String, ItemStackHandler> getCurioMap() {
            return ImmutableSortedMap.copyOf(this.curioSlots);
        }

        @Override
        public void setCurioMap(SortedMap<String, ItemStackHandler> map) {

            this.curioSlots = map;
            this.prevCurioSlots.clear();
            for (Map.Entry<String, ItemStackHandler> entry : this.curioSlots.entrySet()) {
                this.prevCurioSlots.put(entry.getKey(), new ItemStackHandler(entry.getValue().getSlots()));
            }
        }

        @Override
        public ImmutableSortedMap<String, ItemStackHandler> getPreviousCurioMap() {
            return ImmutableSortedMap.copyOf(this.prevCurioSlots);
        }

        @Override
        public void enableCurio(String identifier) {
            CurioType type = CuriosAPI.getType(identifier);

            if (type != null) {
                this.curioSlots.putIfAbsent(identifier, new ItemStackHandler(type.getSize()));
                this.prevCurioSlots.putIfAbsent(identifier, new ItemStackHandler(type.getSize()));

                if (wearer.isServerWorld() && wearer instanceof EntityPlayerMP) {
                    NetworkHandler.INSTANCE.sendTo(new SPacketSyncActive(wearer.getEntityId(), identifier, false),
                            ((EntityPlayerMP)wearer).connection.getNetworkManager(), NetworkDirection.PLAY_TO_CLIENT);
                }
            }
        }

        @Override
        public void disableCurio(String identifier) {
            this.curioSlots.remove(identifier);
            this.prevCurioSlots.remove(identifier);

            if (wearer.isServerWorld() && wearer instanceof EntityPlayerMP) {
                NetworkHandler.INSTANCE.sendTo(new SPacketSyncActive(wearer.getEntityId(), identifier, true),
                        ((EntityPlayerMP)wearer).connection.getNetworkManager(), NetworkDirection.PLAY_TO_CLIENT);
            }
        }

        @Override
        public void addCurioSlot(String identifier, int amount) {
            ItemStackHandler stackHandler = this.curioSlots.get(identifier);

            if (stackHandler != null) {

                if (wearer.isServerWorld()) {

                    if (amount < 0) {
                        NonNullList<ItemStack> drops = NonNullList.create();

                        for (int i = stackHandler.getSlots() - 1; i >= stackHandler.getSlots() + amount; i--) {
                            ItemStack stack = stackHandler.getStackInSlot(i);
                            drops.add(stackHandler.getStackInSlot(i));
                            CuriosAPI.getCurio(stack).ifPresent(curio -> {
                                if (!stack.isEmpty()) {
                                    curio.onUnequipped(stack, identifier, wearer);
                                    wearer.getAttributeMap().removeAttributeModifiers(curio.getAttributeModifiers(identifier, stack));
                                }
                            });
                            stackHandler.setStackInSlot(i, ItemStack.EMPTY);
                        }

                        if (wearer instanceof EntityPlayer) {

                            for (ItemStack drop : drops) {
                                ItemHandlerHelper.giveItemToPlayer((EntityPlayer) wearer, drop);
                            }
                        } else {

                            for (ItemStack drop : drops) {
                                wearer.entityDropItem(drop, 0.0f);
                            }
                        }
                    }

                    if (wearer instanceof EntityPlayerMP) {
                        NetworkHandler.INSTANCE.sendTo(new SPacketSyncSize(wearer.getEntityId(), identifier, amount),
                                ((EntityPlayerMP) wearer).connection.getNetworkManager(), NetworkDirection.PLAY_TO_CLIENT);
                    }
                }
                NonNullList<ItemStack> copy = NonNullList.create();

                for (int i = 0; i < stackHandler.getSlots(); i++) {
                    copy.add(stackHandler.getStackInSlot(i).copy());
                }
                stackHandler.setSize(stackHandler.getSlots() + amount);

                for (int i = 0; i < copy.size(); i++) {
                    stackHandler.setStackInSlot(i, copy.get(i));
                }

                ItemStackHandler prevStackHandler = this.prevCurioSlots.get(identifier);

                if (prevStackHandler != null) {
                    NonNullList<ItemStack> copyPrevious = NonNullList.create();

                    for (int i = 0; i < prevStackHandler.getSlots(); i++) {
                        copyPrevious.add(prevStackHandler.getStackInSlot(i).copy());
                    }
                    prevStackHandler.setSize(stackHandler.getSlots() + amount);

                    for (int i = 0; i < copy.size(); i++) {
                        prevStackHandler.setStackInSlot(i, copyPrevious.get(i));
                    }
                }
            }
        }

        @Nullable
        @Override
        public EntityLivingBase getWearer() {
            return this.wearer;
        }
    }

    public static class Provider implements ICapabilitySerializable<INBTBase> {

        final LazyOptional<ICurioItemHandler> optional;
        final ICurioItemHandler handler;

        Provider(final EntityLivingBase livingBase) {
            this.handler = new CurioInventoryWrapper(livingBase);
            this.optional = LazyOptional.of(() -> handler);
        }

        @SuppressWarnings("ConstantConditions")
        @Nonnull
        @Override
        public <T> LazyOptional<T> getCapability(@Nullable Capability<T> capability, EnumFacing facing) {
            return CuriosCapability.INVENTORY.orEmpty(capability, optional);
        }

        @SuppressWarnings("ConstantConditions")
        @Override
        public INBTBase serializeNBT() {
            return CuriosCapability.INVENTORY.writeNBT(handler, null);
        }

        @SuppressWarnings("ConstantConditions")
        @Override
        public void deserializeNBT(INBTBase nbt) {
            CuriosCapability.INVENTORY.readNBT(handler, null, nbt);
        }
    }
}