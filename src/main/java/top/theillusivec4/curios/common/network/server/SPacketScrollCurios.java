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

package top.theillusivec4.curios.common.network.server;

import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.inventory.container.Container;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;
import top.theillusivec4.curios.common.inventory.CuriosContainer;

public class SPacketScrollCurios {

  private int windowId;
  private int index;

  public SPacketScrollCurios(int windowId, int index) {

    this.windowId = windowId;
    this.index = index;
  }

  public static void encode(SPacketScrollCurios msg, PacketBuffer buf) {

    buf.writeInt(msg.windowId);
    buf.writeInt(msg.index);
  }

  public static SPacketScrollCurios decode(PacketBuffer buf) {

    return new SPacketScrollCurios(buf.readInt(), buf.readInt());
  }

  public static void handle(SPacketScrollCurios msg, Supplier<NetworkEvent.Context> ctx) {

    ctx.get().enqueueWork(() -> {
      ClientPlayerEntity sp = Minecraft.getInstance().player;
      Container container = sp.openContainer;

      if (container instanceof CuriosContainer && container.windowId == msg.windowId) {
        ((CuriosContainer) container).scrollToIndex(msg.index);
      }
    });
    ctx.get().setPacketHandled(true);
  }
}
