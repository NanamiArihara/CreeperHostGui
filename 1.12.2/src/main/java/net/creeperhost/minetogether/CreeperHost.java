package net.creeperhost.minetogether;

import com.google.gson.Gson;
import net.creeperhost.minetogether.api.CreeperHostAPI;
import net.creeperhost.minetogether.api.ICreeperHostMod;
import net.creeperhost.minetogether.api.IServerHost;
import net.creeperhost.minetogether.chat.ChatHandler;
import net.creeperhost.minetogether.common.HostHolder;
import net.creeperhost.minetogether.common.IHost;
import net.creeperhost.minetogether.common.Config;
import net.creeperhost.minetogether.common.GDPR;
import net.creeperhost.minetogether.gui.serverlist.data.Invite;
import net.creeperhost.minetogether.paul.Callbacks;
import net.creeperhost.minetogether.paul.CreeperHostServerHost;
import net.creeperhost.minetogether.proxy.IProxy;
import net.creeperhost.minetogether.serverlist.data.Friend;
import net.creeperhost.minetogether.siv.QueryGetter;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.FMLClientHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Random;

@Mod(
    modid = CreeperHost.MOD_ID,
    name = CreeperHost.NAME,
    version = CreeperHost.VERSION,
    acceptableRemoteVersions = "*",
    acceptedMinecraftVersions = "1.9.4,1.10.2,1.11.2",
    guiFactory = "net.creeperhost.minetogether.gui.config.GuiCreeperConfigFactory"
)
public class CreeperHost implements ICreeperHostMod, IHost
{

    public static final String MOD_ID = "minetogether";
    public static final String NAME = "MineTogether";
    public static final String VERSION = "@VERSION@";
    public static final Logger logger = LogManager.getLogger("minetogether");
    public ArrayList<String> mutedUsers = new ArrayList<>();

    @Mod.Instance(value = "minetogether", owner = "minetogether")
    public static CreeperHost instance;

    @SidedProxy(clientSide = "net.creeperhost.minetogether.proxy.Client", serverSide = "net.creeperhost.minetogether.proxy.Server")
    public static IProxy proxy;
    public final Object inviteLock = new Object();
    public ArrayList<IServerHost> implementations = new ArrayList<IServerHost>();
    public IServerHost currentImplementation;
    public File configFile;
    public int curServerId = -1;
    public Invite handledInvite;
    public boolean active = true;
    public Invite invite;
    public GDPR gdpr;
    public String activeMinigame;
    public int minigameID;
    public boolean trialMinigame;
    public long joinTime;
    String toastText;
    long endTime;
    long fadeTime;
    private QueryGetter queryGetter;
    private String lastCurse = "";
    private Random randomGenerator;
    private CreeperHostServerHost implement;

    public String ourNick;
    public File mutedUsersFile;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event)
    {
        configFile = event.getSuggestedConfigurationFile();
        InputStream configStream = null;
        try
        {
            String configString;
            if (configFile.exists())
            {
                configStream = new FileInputStream(configFile);
                configString = IOUtils.toString(configStream);
            }
            else
            {
                File parent = configFile.getParentFile();
                File tempConfigFile = new File(parent, "creeperhost.cfg");
                if (tempConfigFile.exists())
                {
                    configStream = new FileInputStream(tempConfigFile);
                    configString = IOUtils.toString(configStream);
                }
                else
                {
                    configString = "{}";
                }

            }

            Config.loadConfig(configString);
        }
        catch (Throwable t)
        {
            logger.error("Fatal error, unable to read config. Not starting mod.", t);
            active = false;
        }
        finally
        {
            try
            {
                if (configStream != null)
                {
                    configStream.close();
                }
            }
            catch (Throwable t)
            {
            }
            if (!active)
                return;
        }

        saveConfig();

        PacketHandler.packetRegister();

        if (event.getSide() != Side.SERVER)
        {
            HostHolder.host = this;
            File gdprFile = new File("config/minetogether/gdpr.txt");
            gdpr = new GDPR(gdprFile);
            ourNick = "MT" + Callbacks.getPlayerHash(CreeperHost.proxy.getUUID()).substring(0, 15);
            MinecraftForge.EVENT_BUS.register(new EventHandler());
            proxy.registerKeys();
        }
    }

    public void saveConfig()
    {
        FileOutputStream configOut = null;
        try
        {
            configOut = new FileOutputStream(configFile);
            IOUtils.write(Config.saveConfig(), configOut);
            configOut.close();
        }
        catch (Throwable t)
        {
        }
        finally
        {
            try
            {
                if (configOut != null)
                {
                    configOut.close();
                }
            }
            catch (Throwable t)
            {
            }
        }

        if (Config.getInstance().isCreeperhostEnabled())
        {
            CreeperHost.instance.implementations.remove(implement);
            implement = new CreeperHostServerHost();
            CreeperHostAPI.registerImplementation(implement);
        }

        if (!Config.getInstance().isCreeperhostEnabled())
        {
            CreeperHost.instance.implementations.remove(implement);
            implement = null;
        }
    }

    public void updateCurse()
    {
        if (!Config.getInstance().curseProjectID.equals(lastCurse) && Config.getInstance().isCreeperhostEnabled())
        {
            Config.getInstance().setVersion(Callbacks.getVersionFromCurse(Config.getInstance().curseProjectID));
        }

        lastCurse = Config.getInstance().curseProjectID;
    }

    public void setRandomImplementation()
    {
        if (randomGenerator == null)
            randomGenerator = new Random();
        if (implementations.size() == 0)
        {
            currentImplementation = null;
            return;
        }
        int random = randomGenerator.nextInt(implementations.size());
        currentImplementation = implementations.get(random);
    }

    public IServerHost getImplementation()
    {
        return currentImplementation;
    }

    @Override
    public void registerImplementation(IServerHost serverHost)
    {
        implementations.add(serverHost);
    }

    public void makeQueryGetter()
    {
        try
        {
            if (FMLClientHandler.instance().getClientToServerNetworkManager() != null)
            {
                SocketAddress socketAddress = FMLClientHandler.instance().getClientToServerNetworkManager().getRemoteAddress();

                String host = "127.0.0.1";
                int port = 25565;

                if (socketAddress instanceof InetSocketAddress)
                {
                    InetSocketAddress add = (InetSocketAddress) socketAddress;
                    host = add.getHostName();
                    port = add.getPort();
                }

                queryGetter = new QueryGetter(host, port);
            }
        }
        catch (Throwable t)
        {
            // Catch _ALL_ errors. We should _NEVER_ crash.
        }

    }

    public QueryGetter getQueryGetter()
    {
        if (queryGetter == null)
        {
            makeQueryGetter();
        }
        return queryGetter;
    }

    public void displayToast(String text, int duration)
    {
        toastText = text;
        endTime = System.currentTimeMillis() + duration;
        fadeTime = endTime + 500;
    }

    public void clearToast(boolean fade)
    {
        endTime = System.currentTimeMillis();
        fadeTime = endTime + (fade ? 500 : 0);
    }

    @Override
    public ArrayList<Friend> getFriends()
    {
        return Callbacks.getFriendsList(false);
    }

    final Object friendLock = new Object();
    String friend = null;
    boolean friendMessage = false;

    @Override
    public void friendEvent(String name, boolean isMessage)
    {
        synchronized (friendLock)
        {
            friend = ChatHandler.getNameForUser(name);
            friendMessage = isMessage;
        }
    }

    @Override
    public Logger getLogger() {
        return logger;
    }

    public void muteUser(String user)
    {
        mutedUsers.add(user);
        Gson gson = new Gson();
        try
        {
            FileUtils.writeStringToFile(mutedUsersFile, gson.toJson(mutedUsers));
        }
        catch (IOException e)
        {
        }
    }
}