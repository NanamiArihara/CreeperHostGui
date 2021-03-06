package net.creeperhost.minetogether;

import cpw.mods.fml.client.event.ConfigChangedEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;
import net.creeperhost.minetogether.api.Order;
import net.creeperhost.minetogether.common.Config;
import net.creeperhost.minetogether.gui.GuiGetServer;
import net.creeperhost.minetogether.gui.GuiProgressDisconnected;
import net.creeperhost.minetogether.gui.GuiServerInfo;
import net.creeperhost.minetogether.gui.element.ButtonCreeper;
import net.creeperhost.minetogether.gui.mpreplacement.CreeperHostServerSelectionList;
import net.creeperhost.minetogether.gui.serverlist.data.Friend;
import net.creeperhost.minetogether.gui.serverlist.data.Invite;
import net.creeperhost.minetogether.gui.serverlist.data.ServerListNoEdit;
import net.creeperhost.minetogether.gui.serverlist.gui.GuiFriendsList;
import net.creeperhost.minetogether.gui.serverlist.gui.GuiInvited;
import net.creeperhost.minetogether.gui.serverlist.gui.GuiMultiplayerPublic;
import net.creeperhost.minetogether.paul.Callbacks;
import net.creeperhost.minetogether.proxy.Client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.network.NetworkManager;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent.ActionPerformedEvent;
import net.minecraftforge.client.event.GuiScreenEvent.InitGuiEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class EventHandler
{

    private static final int MAIN_BUTTON_ID = 30051988;
    private static final int MP_BUTTON_ID = 8008135;
    private static final int FRIEND_BUTTON_ID = 1337420;

    private static GuiServerInfo guiServerInfo = new GuiServerInfo();
    private static Field reasonField = null;
    private static Field messageField = null;
    private static Field parentField = null;
    private static Field networkManagerField = null;
    private static NetworkManager lastNetworkManager = null;
    private static Field serverListSelectorField;
    private static Field serverListInternetField;
    private static int ticks = 0;
    private final ResourceLocation earlyResource = new ResourceLocation("textures/gui/achievement/achievement_background.png");
    Field serverListField = null;
    Field editButtonField = null;
    Minecraft mc = Minecraft.getMinecraft();
    GuiScreen fakeGui = new GuiScreen()
    {
    };
    String mcVersion;
    int u = 0;
    int v = 0;
    private GuiMultiplayer lastInitialized = null;
    private ServerListNoEdit ourServerList;
    private boolean hasJoinedWorld;
    private Thread inviteCheckThread;
    private int inviteTicks = -1;

    public static NetworkManager getNetworkManager(GuiConnecting con)
    {
        long time = System.currentTimeMillis() + 3000;
        try
        {
            if (networkManagerField == null)
            {
                networkManagerField = ReflectionHelper.findField(GuiConnecting.class, "field_146371_g", "networkManager");
                networkManagerField.setAccessible(true);
            }

            NetworkManager manager = null;
            while (manager == null) // loop to wait until networkManager is set.
            {
                if (System.currentTimeMillis() > time)
                    break;
                manager = (NetworkManager) networkManagerField.get(con);
            }

            return manager;
        }
        catch (Exception e)
        {
            e.printStackTrace();
            return null;
        }
    }

    @SubscribeEvent
    public void guiOpen(GuiOpenEvent event)
    {
        GuiScreen gui = event.gui;
        GuiScreen curGui = Minecraft.getMinecraft().currentScreen;
        if (gui instanceof GuiDisconnected && !(gui instanceof GuiProgressDisconnected))
        {
            GuiDisconnected dc = (GuiDisconnected) gui;
            if (reasonField == null)
            {
                reasonField = ReflectionHelper.findField(gui.getClass(), "field_146306_a", "reason");
                reasonField.setAccessible(true);
            }

            if (messageField == null)
            {
                messageField = ReflectionHelper.findField(gui.getClass(), "field_146304_f", "message");
                messageField.setAccessible(true);
            }

            if (parentField == null)
            {
                parentField = ReflectionHelper.findField(gui.getClass(), "field_146307_h", "parentScreen");
                parentField.setAccessible(true);
            }

            try
            {
                String reason = (String) reasonField.get(dc);
                IChatComponent message = (IChatComponent) messageField.get(dc);

                if (curGui instanceof GuiProgressDisconnected)
                {
                    if (message.getFormattedText().contains("Server is still pre-generating!"))
                    {
                        GuiProgressDisconnected curDiscon = (GuiProgressDisconnected) curGui;
                        curDiscon.update(reason, message);
                        event.setCanceled(true);
                    }
                }
                else if (message.getFormattedText().contains("Server is still pre-generating!"))
                {
                    event.gui = new GuiProgressDisconnected((GuiScreen) parentField.get(dc), reason, message, lastNetworkManager);
                    lastNetworkManager = null;
                }
            }
            catch (IllegalAccessException e)
            {
            }
        }
        else if (gui instanceof GuiConnecting)
        {
            //lastNetworkManager = getNetworkManager((GuiConnecting) gui);
        }
    }

    @SubscribeEvent
    public void onInitGui(InitGuiEvent.Post event)
    {
        final GuiScreen gui = event.gui;
        if (Config.getInstance().isMainMenuEnabled() && gui instanceof GuiMainMenu)
        {
            CreeperHost.instance.setRandomImplementation();
            if (CreeperHost.instance.getImplementation() == null)
                return;
            List<GuiButton> buttonList = event.buttonList;
            if (buttonList != null)
            {
                buttonList.add(new ButtonCreeper(MAIN_BUTTON_ID, gui.width / 2 + 104, gui.height / 4 + 48 + 72 + 12));
            }
        }
        else if (gui instanceof GuiMultiplayer && !(gui instanceof GuiMultiplayerPublic) && lastInitialized != gui)
        {
            GuiMultiplayer mpGUI = (GuiMultiplayer) gui;
			if (CreeperHost.instance.getImplementation() == null)
				CreeperHost.instance.setRandomImplementation();
            if (Config.getInstance().isMpMenuEnabled() && CreeperHost.instance.getImplementation() != null)
            {
                try
                {
                    if (serverListSelectorField == null)
                    {
                        serverListSelectorField = ReflectionHelper.findField(GuiMultiplayer.class, "field_146803_h", "serverListSelector");
                        serverListSelectorField.setAccessible(true);
                    }

                    if (serverListInternetField == null)
                    {
                        serverListInternetField = ReflectionHelper.findField(ServerSelectionList.class, "field_148198_l", "serverListInternet");
                        serverListInternetField.setAccessible(true);
                    }

                    ServerSelectionList serverListSelector = (ServerSelectionList) serverListSelectorField.get(mpGUI); // Get the old selector
                    List serverListInternet = (List) serverListInternetField.get(serverListSelector); // Get the list from inside it
                    CreeperHostServerSelectionList ourList = new CreeperHostServerSelectionList(mpGUI, Minecraft.getMinecraft(), mpGUI.width, mpGUI.height, 32, mpGUI.height - 64, 36);
                    ourList.replaceList(serverListInternet);
                    serverListInternetField.set(ourList, serverListInternet);
                    serverListSelectorField.set(mpGUI, ourList);
                }
                catch (Throwable e)
                {
                    CreeperHost.logger.warn("Reflection to alter server list failed.", e);
                }
            }

            if (Config.getInstance().isServerListEnabled())
            {
                try
                {
                    if (serverListField == null)
                    {
                        serverListField = ReflectionHelper.findField(GuiMultiplayer.class, "field_146804_i"); // TODO: Needs non srg name
                        serverListField.setAccessible(true);
                    }

                    ourServerList = new ServerListNoEdit(Minecraft.getMinecraft());
                    serverListField.set(mpGUI, ourServerList);
                    ourServerList.loadServerList();
                }
                catch (IllegalAccessException e)
                {
                }
            }

            lastInitialized = mpGUI;

        }

        if (Config.getInstance().isServerListEnabled())
        {
            if (gui instanceof GuiMultiplayer && !(gui instanceof GuiMultiplayerPublic))
            {
                event.buttonList.add(new GuiButton(MP_BUTTON_ID, gui.width - 100 - 5, 5, 100, 20, I18n.format("creeperhost.multiplayer.public")));
                GuiButton editButton = null;
                for (int i = 0; i < event.buttonList.size(); i++)
                {
                    GuiButton button = (GuiButton) event.buttonList.get(i);
                    if (button.id == 7)
                    {
                        editButton = button;
                        break;
                    }
                }

                if (editButton != null)
                {
                    event.buttonList.remove(editButton);
                    ServerSelectionList list = null;

                    if (serverListSelectorField == null)
                    {
                        serverListSelectorField = ReflectionHelper.findField(GuiMultiplayer.class, "field_146803_h", "serverListSelector");
                        serverListSelectorField.setAccessible(true);
                    }

                    try
                    {
                        list = (ServerSelectionList) serverListSelectorField.get(gui);
                    }
                    catch (IllegalAccessException e)
                    {
                    }

                    final ServerSelectionList finalList = list;
                    event.buttonList.add(editButton = new GuiButton(7, gui.width / 2 - 154, gui.height - 28, 70, 20, I18n.format("selectServer.edit"))
                    {
                        @Override
                        public void drawButton(Minecraft p_146112_1_, int p_146112_2_, int p_146112_3_)
                        {
                            int placeInList = finalList.func_148193_k();
                            GuiListExtended.IGuiListEntry iguilistentry = placeInList < 0 ? null : finalList.getListEntry(placeInList);
                            if (iguilistentry instanceof ServerListEntryNormal)
                            {
                                if (ourServerList.isLocked(placeInList))
                                {
                                    enabled = false;
                                    if (field_146123_n)
                                    {
                                        ((GuiMultiplayer) gui).func_146793_a("Cannot edit as was added from public server list!");
                                    }
                                }
                                else
                                {
                                    enabled = true;
                                }
                            }
                            super.drawButton(p_146112_1_, p_146112_2_, p_146112_3_);
                        }
                    });

                    editButton.enabled = false;

                    if (editButtonField == null)
                    {
                        editButtonField = ReflectionHelper.findField(GuiMultiplayer.class, "field_146810_r"); //TODO: Needs non srg name
                        editButtonField.setAccessible(true);
                    }

                    try
                    {
                        editButtonField.set(gui, editButton);
                    }
                    catch (IllegalAccessException e)
                    {
                    }
                }

            }

            if (gui instanceof GuiIngameMenu)
                event.buttonList.add(new GuiButton(FRIEND_BUTTON_ID, gui.width - 100 - 5, 5, 100, 20, I18n.format("creeperhost.multiplayer.friends")));
        }
    }

    @SubscribeEvent
    public void serverLoginEvent(FMLNetworkEvent.ClientConnectedToServerEvent event)
    {
        hasJoinedWorld = false;
    }

    @SubscribeEvent
    public void onEntityJoinedWorld(EntityJoinWorldEvent event)
    {
        if (!Config.getInstance().isSivIntegration())
            return;
        if (event.world.isRemote && !hasJoinedWorld && Minecraft.getMinecraft().thePlayer != null)
        {
            hasJoinedWorld = true;
            CreeperHost.instance.makeQueryGetter();
            if (CreeperHost.instance.getQueryGetter() != null)
            {
                CreeperHost.instance.getQueryGetter().run();
            }
        }
    }

    @SubscribeEvent
    public void onActionPerformed(ActionPerformedEvent.Pre event)
    {
        GuiScreen gui = Util.getGuiFromEvent(event);
        GuiButton button = Util.getButton(event);
        if (gui instanceof GuiMainMenu)
        {
            if (button != null && button.id == MAIN_BUTTON_ID)
            {
                Minecraft.getMinecraft().displayGuiScreen(GuiGetServer.getByStep(0, new Order()));
            }
        }
        else if (gui instanceof GuiMultiplayer)
        {
            if (button != null && button.id == MP_BUTTON_ID)
            {
                Minecraft.getMinecraft().displayGuiScreen(new GuiMultiplayerPublic(gui));
            }
        }
        else if (gui instanceof GuiIngameMenu && button.id == FRIEND_BUTTON_ID)
        {
            CreeperHost.proxy.openFriendsGui();
        }
    }

    @SubscribeEvent
    public void onRenderGameOverlay(RenderGameOverlayEvent event)
    {
        if (!Config.getInstance().isSivIntegration())
            return;
        if (event.type != RenderGameOverlayEvent.ElementType.PLAYER_LIST)
        {
            return;
        }
        if (!(Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL)) || Minecraft.getMinecraft().isIntegratedServerRunning() || !guiServerInfo.getIsPlayerOpped())
        {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();

        ScaledResolution resolution = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        guiServerInfo.setWorldAndResolution(mc, resolution.getScaledWidth(), resolution.getScaledHeight());
        if (guiServerInfo.renderServerInfo())
        {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void tickEvent(TickEvent.ClientTickEvent event)
    {
        if (!Config.getInstance().isSivIntegration())
            return;
        guiServerInfo.doTick();
        if (!((Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL)) && Minecraft.getMinecraft().gameSettings.keyBindPlayerList.getIsKeyPressed()) || Minecraft.getMinecraft().isIntegratedServerRunning() || !guiServerInfo.getIsPlayerOpped())
        {
            return;
        }

        try
        {

            if (ticks == 0)
            {
                ticks = 40;
                //Update
                if (CreeperHost.instance.getQueryGetter() != null)
                {
                    CreeperHost.instance.getQueryGetter().run();
                }
            }
            ticks--;
        }
        catch (Throwable t)
        {
            // Catch _ALL_ errors. We should _NEVER_ crash.
        }
    }

    @SubscribeEvent
    public void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent eventArgs)
    {
        if (!CreeperHost.MOD_ID.equals(eventArgs.modID))
        {
            return;
        }

        CreeperHost.instance.saveConfig();
    }

    @SubscribeEvent
    public void clientDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent evt)
    {
        CreeperHost.instance.curServerId = -1;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent evt)
    {
        inviteTicks = (inviteTicks + 1) % 20;
        if (inviteTicks != 0)
            return;

        if (Config.getInstance().isServerListEnabled())
        {
            if (inviteCheckThread == null)
            {
                inviteCheckThread = new Thread(new Runnable()
                {

                    @Override
                    public void run()
                    {
                        while (Config.getInstance().isServerListEnabled())
                        {
                            Invite tempInvite = null;

                            try
                            {
                                tempInvite = Callbacks.getInvite();
                            }
                            catch (Exception e)
                            {
                                // carry on - we'll just try again later, saves thread dying.
                            }

                            synchronized (CreeperHost.instance.inviteLock)
                            {
                                if (tempInvite != null)
                                    CreeperHost.instance.invite = tempInvite;
                            }

                            try
                            {
                                Thread.sleep(15000);
                            }
                            catch (InterruptedException e)
                            {
                            }
                        }
                    }
                });
                inviteCheckThread.setDaemon(true);
                inviteCheckThread.setName("MineTogether invite check thread");
                inviteCheckThread.start();
            }

            boolean handled = false;
            synchronized (CreeperHost.instance.inviteLock)
            {
                if (CreeperHost.instance.invite != null)
                {
                    CreeperHost.instance.handledInvite = CreeperHost.instance.invite;
                    CreeperHost.instance.invite = null;

                    handled = true;
                }
            }

            if (handled)
            {
                ArrayList<Friend> friendsList = Callbacks.getFriendsList(true);
                String friendName = "Unknown";

                for (Friend friend : friendsList)
                {
                    if (friend.getCode().equals(CreeperHost.instance.handledInvite.by))
                    {
                        friendName = friend.getName();
                        CreeperHost.instance.handledInvite.by = friendName;
                        break;
                    }
                }
                if (mc.currentScreen != null && mc.currentScreen instanceof GuiFriendsList)
                {
                    CreeperHost.proxy.openFriendsGui();
                }
                else
                {
                    CreeperHost.instance.displayToast(I18n.format("creeperhost.multiplayer.invitetoast", GameSettings.getKeyDisplayString(((Client) CreeperHost.proxy).openGuiKey.getKeyCode())), 15000);
                }

            }

        }
    }

    private ResourceLocation getToastResourceLocation()
    {
        u = 96;
        v = 202;
        return earlyResource;
    }

    @SubscribeEvent
    public void guiRendered(TickEvent.RenderTickEvent evt)
    {
        if (CreeperHost.instance.toastText != null)
        {
            long curTime = System.currentTimeMillis();
            if (CreeperHost.instance.fadeTime > curTime)
            {
                long fadeDiff = CreeperHost.instance.fadeTime - CreeperHost.instance.endTime;
                long curFade = Math.min(CreeperHost.instance.fadeTime - curTime, fadeDiff);
                float alpha = (float) curFade / (float) fadeDiff;

                RenderHelper.disableStandardItemLighting();
                GL11.glColor4f(1.0F, 1.0F, 1.0F, alpha);
                mc.renderEngine.bindTexture(getToastResourceLocation());
                ScaledResolution res = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
                drawTexturedModalRect(res.getScaledWidth() - 160, 0, u, v, 160, 32);
                GL11.glEnable(GL11.GL_BLEND);
                OpenGlHelper.glBlendFunc(770, 771, 1, 0);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                int textColour = (0xFFFFFF << 32) | ((int) (alpha * 255) << 24);
                mc.fontRenderer.drawSplitString(CreeperHost.instance.toastText, res.getScaledWidth() - 160 + 5, 6, 160, textColour);
            }
            else
            {
                CreeperHost.instance.toastText = null;
            }
        }
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event)
    {
        if (Config.getInstance().isServerListEnabled() && ((Client) CreeperHost.proxy).openGuiKey.isPressed())
        {
            if (CreeperHost.instance.handledInvite != null)
            {
                CreeperHost.instance.clearToast(false);
                mc.displayGuiScreen(new GuiInvited(CreeperHost.instance.handledInvite, mc.currentScreen));
                CreeperHost.instance.handledInvite = null;
            }
            else
                CreeperHost.proxy.openFriendsGui();
        }
    }

    //private float zLevel = 0;

    private void drawTexturedModalRect(int x, int y, int textureX, int textureY, int width, int height)
    {
        fakeGui.drawTexturedModalRect(x, y, textureX, textureY, width, height);
    }
}
