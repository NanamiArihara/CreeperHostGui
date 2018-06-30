package net.creeperhost.minetogether.gui.chat;

import net.creeperhost.minetogether.chat.ChatHandler;
import net.creeperhost.minetogether.gui.element.DropdownButton;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentUtils;
import net.minecraft.util.text.TextFormatting;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

class Target implements DropdownButton.IDropdownOption
{
    private final String targetName;
    private static ArrayList<Target> possibleValsCache = new ArrayList<>();
    private static boolean updating = false;
    private final String internalTarget;
    private static Map<String, String> oldFriends;
    private static int oldMessagesSize;

    private Target(String targetName, String internalTarget)
    {
        this.internalTarget = internalTarget;
        this.targetName = targetName;
    }

    @Override
    public String getTranslate(DropdownButton.IDropdownOption currentDO, boolean dropdownOpen)
    {
        Target current = (Target) currentDO;
        boolean newMessages = false;
        if (current == this)
        {
            for(DropdownButton.IDropdownOption targetObj: getPossibleVals())
            {
                Target target = (Target) targetObj;
                if (ChatHandler.hasNewMessages(target.getInternalTarget()))
                {
                    if (!dropdownOpen)
                    {
                        newMessages = true;
                        break;
                    }
                }
            }
        } else {
            newMessages = ChatHandler.hasNewMessages(getInternalTarget());
        }
        if (newMessages)
        {
            TextComponentString str = new TextComponentString(targetName);
            str.appendSibling(new TextComponentString(" \u2022").setStyle(new Style().setColor(TextFormatting.RED)));
            return str.getFormattedText();
        }

        return targetName;
    }

    @Override
    public void updateDynamic()
    {
        updateCache(this);
    }

    public static void updateCache()
    {
        updateCache(null);
    }

    public static void updateCache(Target current)
    {
        int chatSize = ChatHandler.messages.size();
        if (updating || oldFriends == ChatHandler.friends && chatSize == oldMessagesSize)
            return;

        updating = true;

        ArrayList<Target> oldVals = possibleValsCache;

        HashSet<Target> tempSet = new HashSet<>();

        possibleValsCache = new ArrayList<>();
        tempSet.add(new Target("Main", ChatHandler.CHANNEL));
        if (current != null && !tempSet.contains(current))
            tempSet.add(current);

        for (Map.Entry<String, String> friend : ChatHandler.friends.entrySet())
        {
            Target tempTarget = new Target(friend.getValue(), friend.getKey());
            if (!tempSet.contains(tempTarget))
            {
                if (oldVals.contains(tempTarget))
                {
                    tempTarget = oldVals.get(oldVals.indexOf(tempTarget));
                }
                tempSet.add(tempTarget);
            }

        }

        for (String chat : ChatHandler.messages.keySet())
        {
            if (chat.equals(ChatHandler.CHANNEL))
                continue;
             for(Target target: oldVals)
             {
                 if (target.getInternalTarget().equals(chat))
                 {
                     tempSet.add(target);
                     break;
                 }
             }
        }

        possibleValsCache = new ArrayList<>(tempSet);

        updating = false;
        oldFriends = ChatHandler.friends;
        oldMessagesSize = chatSize;
    }

    public static Target getMainTarget()
    {
        updateCache();
        for (Target defTar: possibleValsCache)
        {
            if(defTar.getInternalTarget().equals(ChatHandler.CHANNEL))
            {
                return defTar;
            }
        }
        return possibleValsCache.get(0);
    }

    @Override
    public List<DropdownButton.IDropdownOption> getPossibleVals()
    {
        return (ArrayList)possibleValsCache;
    }

    public String getInternalTarget()
    {
        return internalTarget;
    }

    @Override
    public boolean equals(Object obj)
    {
        return obj instanceof Target && (((Target) obj).internalTarget.equals(internalTarget)) && ((Target) obj).targetName.equals(targetName);
    }

    @Override
    public int hashCode()
    {
        return internalTarget.hashCode() + targetName.hashCode();
    }
}