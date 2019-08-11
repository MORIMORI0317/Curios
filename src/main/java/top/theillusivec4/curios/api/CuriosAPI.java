/*
 * Copyright (C) 2018-2019  C4
 *
 * This file is part of Curios, a mod made for Minecraft.
 *
 * Curios is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Curios is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Curios.  If not, see <https://www.gnu.org/licenses/>.
 */

package top.theillusivec4.curios.api;

import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.LazyOptional;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.logging.log4j.util.TriConsumer;
import top.theillusivec4.curios.api.capability.CuriosCapability;
import top.theillusivec4.curios.api.capability.ICurio;
import top.theillusivec4.curios.api.capability.ICurioItemHandler;
import top.theillusivec4.curios.api.inventory.CurioStackHandler;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class CuriosAPI {

  /**
   * Holds a reference to the Curios modid
   */
  public static final String MODID = "curios";

  /**
   * @param stack The ItemStack to get the curio capability from
   * @return LazyOptional of the curio capability attached to the ItemStack
   */
  public static LazyOptional<ICurio> getCurio(ItemStack stack) {

    return stack.getCapability(CuriosCapability.ITEM);
  }

  /**
   * @param entityLivingBase The ItemStack to get the curio inventory capability from
   * @return LazyOptional of the curio inventory capability attached to the entity
   */
  public static LazyOptional<ICurioItemHandler> getCuriosHandler(
      @Nonnull final LivingEntity entityLivingBase) {

    return entityLivingBase.getCapability(CuriosCapability.INVENTORY);
  }

  /**
   * Passes three inputs into an internal triple-input consumer that should be used from the
   * single-input consumer in {@link ItemStack#damageItem(int, LivingEntity, Consumer)}
   * <p>
   * This will be necessary in order to trigger break animations in curio slots
   * <p>
   * Example:
   * {
   * stack.damageItem(amount, entity, damager -> CuriosAPI.onBrokenCurio(id, index, damager));
   * }
   *
   * @param id      The {@link CurioType} String identifier
   * @param index   The slot index of the identifier
   * @param damager The entity that is breaking the item
   */
  public static void onBrokenCurio(String id, int index, LivingEntity damager) {

    brokenCurioConsumer.accept(id, index, damager);
  }

  /**
   * @param identifier The unique identifier for the {@link CurioType}
   * @return Optional wrapper of the CurioType from the given identifier, or Optional.empty() if
   * not present.
   */
  public static Optional<CurioType> getType(String identifier) {

    return Optional.ofNullable(idToType.get(identifier));
  }

  /**
   * @return An unmodifiable list of all unique registered identifiers
   */
  public static Set<String> getTypeIdentifiers() {

    return Collections.unmodifiableSet(idToType.keySet());
  }

  /**
   * Gets the first found ItemStack of the item type equipped in a curio slot, or null if no
   * matches were found.
   *
   * @param item             The item to find
   * @param entityLivingBase The wearer of the item to be found
   * @return An instance of {@link ImmutableTriple} indicating the identifier of the curio slot,
   * slot index, and the ItemStack of the first found ItemStack matching the parameters. All
   * values will be empty if no matches were found.
   */
  public static Optional<ImmutableTriple<String, Integer, ItemStack>> getCurioEquipped(Item item,
                                                                                       @Nonnull final LivingEntity entityLivingBase) {

    ImmutableTriple<String, Integer, ItemStack> result =
        getCuriosHandler(entityLivingBase).map(handler -> {
          Set<String> tags = getCurioTags(item);

          for (String id : tags) {
            CurioStackHandler stackHandler = handler.getStackHandler(id);

            if (stackHandler != null) {

              for (int i = 0; i < stackHandler.getSlots(); i++) {
                ItemStack stack = stackHandler.getStackInSlot(i);

                if (!stack.isEmpty() && item == stack.getItem()) {
                  return new ImmutableTriple<>(id, i, stack);
                }
              }
            }
          }
          return new ImmutableTriple<>("", 0, ItemStack.EMPTY);
        }).orElse(new ImmutableTriple<>("", 0, ItemStack.EMPTY));
    return result.getLeft().isEmpty() ? Optional.empty() : Optional.of(result);
  }

  /**
   * Gets the first found ItemStack of the item type equipped in a curio slot that matches the
   * filter, or null if no matches were found.
   *
   * @param filter           The filter to test the ItemStack against
   * @param entityLivingBase The wearer of the item to be found
   * @return An instance of {@link ImmutableTriple} indicating the identifier of the curio slot,
   * slot index, and the ItemStack of the first found ItemStack matching the parameters. All
   * values will be empty if no matches were found.
   */
  @Nonnull
  public static Optional<ImmutableTriple<String, Integer, ItemStack>> getCurioEquipped(
      Predicate<ItemStack> filter, @Nonnull final LivingEntity entityLivingBase) {

    ImmutableTriple<String, Integer, ItemStack> result =
        getCuriosHandler(entityLivingBase).map(handler -> {

          for (String id : handler.getCurioMap().keySet()) {
            CurioStackHandler stackHandler = handler.getStackHandler(id);

            if (stackHandler != null) {

              for (int i = 0; i < stackHandler.getSlots(); i++) {
                ItemStack stack = stackHandler.getStackInSlot(i);

                if (!stack.isEmpty() && filter.test(stack)) {
                  return new ImmutableTriple<>(id, i, stack);
                }
              }

            }
          }
          return new ImmutableTriple<>("", 0, ItemStack.EMPTY);
        }).orElse(new ImmutableTriple<>("", 0, ItemStack.EMPTY));
    return result.getLeft().isEmpty() ? Optional.empty() : Optional.of(result);
  }

  /**
   * Adds a single slot to the {@link CurioType} with the associated identifier.
   * If the slot to be added is for a type that is not enabled on the entity, it will not be added.
   * For adding slot(s) for types that are not yet available, there must first be a call to
   * {@link CuriosAPI#enableTypeForEntity(String, LivingEntity)}
   *
   * @param id               The identifier of the CurioType
   * @param entityLivingBase The holder of the slot(s)
   */
  public static void addTypeSlotToEntity(String id, final LivingEntity entityLivingBase) {

    addTypeSlotsToEntity(id, 1, entityLivingBase);
  }

  /**
   * Adds multiple slots to the {@link CurioType} with the associated identifier.
   * If the slot to be added is for a type that is not enabled on the entity, it will not be added.
   * For adding slot(s) for types that are not yet available, there must first be a call to
   * {@link CuriosAPI#enableTypeForEntity(String, LivingEntity)}
   *
   * @param id               The identifier of the CurioType
   * @param amount           The number of slots to add
   * @param entityLivingBase The holder of the slots
   */
  public static void addTypeSlotsToEntity(String id, int amount,
                                          final LivingEntity entityLivingBase) {

    getCuriosHandler(entityLivingBase).ifPresent(handler -> handler.addCurioSlot(id, amount));
  }

  /**
   * Removes a single slot to the {@link CurioType} with the associated identifier.
   * If the slot to be removed is the last slot available, it will not be removed.
   * For the removal of the last slot, please see
   * {@link CuriosAPI#disableTypeForEntity(String, LivingEntity)}
   *
   * @param id               The identifier of the CurioType
   * @param entityLivingBase The holder of the slot(s)
   */
  public static void removeTypeSlotFromEntity(String id, final LivingEntity entityLivingBase) {

    removeTypeSlotsFromEntity(id, 1, entityLivingBase);
  }

  /**
   * Removes multiple slots to the {@link CurioType} with the associated identifier.
   * If the slot to be removed is the last slot available, it will not be removed.
   * For the removal of the last slot, please see
   * {@link CuriosAPI#disableTypeForEntity(String, LivingEntity)}
   *
   * @param id               The identifier of the CurioType
   * @param entityLivingBase The holder of the slot(s)
   */
  public static void removeTypeSlotsFromEntity(String id, int amount,
                                               final LivingEntity entityLivingBase) {

    getCuriosHandler(entityLivingBase).ifPresent(handler -> handler.removeCurioSlot(id, amount));
  }

  /**
   * Adds a {@link CurioType} to the entity
   * The number of slots given is the type's default
   *
   * @param id               The identifier of the CurioType
   * @param entityLivingBase The holder of the slot(s)
   */
  public static void enableTypeForEntity(String id, final LivingEntity entityLivingBase) {

    getCuriosHandler(entityLivingBase).ifPresent(handler -> handler.enableCurio(id));
  }

  /**
   * Removes a {@link CurioType} from the entity
   *
   * @param id               The identifier of the CurioType
   * @param entityLivingBase The holder of the slot(s)
   */
  public static void disableTypeForEntity(String id, final LivingEntity entityLivingBase) {

    getCuriosHandler(entityLivingBase).ifPresent(handler -> handler.disableCurio(id));
  }

  /**
   * Retrieves a set of string identifiers from the curio tags associated with the given item
   *
   * @param item The item to retrieve curio tags for
   * @return Unmodifiable list of unique curio identifiers associated with
   * the item
   */
  public static Set<String> getCurioTags(Item item) {

    return item.getTags()
               .stream().filter(tag -> tag.getNamespace().equals(MODID))
               .map(ResourceLocation::getPath)
               .collect(Collectors.toSet());
  }

  private static final ResourceLocation GENERIC_SLOT =
      new ResourceLocation(MODID, "textures/item" + "/empty_generic_slot.png");

  /**
   * @return An unmodifiable map of identifiers and their registered icons
   */
  @Nonnull
  public static ResourceLocation getIcon(String identifier) {

    return idToIcon.getOrDefault(identifier, GENERIC_SLOT);
  }

  /**
   * The maps containing the CurioType and icons with identifiers as keys
   * Try not to access these directly and instead use
   * {@link CuriosAPI#getType(String)} and {@link CuriosAPI#getIcon(String)}
   * DO NOT REGISTER DIRECTLY - Use IMC to send the appropriate
   * {@link top.theillusivec4.curios.api.imc.CurioIMCMessage}
   */
  public static Map<String, CurioType>        idToType = new HashMap<>();
  public static Map<String, ResourceLocation> idToIcon = new HashMap<>();

  public static TriConsumer<String, Integer, LivingEntity> brokenCurioConsumer;

  /**
   * Holder class for IMC message identifiers
   */
  public final static class IMC {

    public static final String REGISTER_TYPE = "register_type";
    public static final String MODIFY_TYPE   = "modify_type";
    public static final String REGISTER_ICON = "register_icon";
  }
}
