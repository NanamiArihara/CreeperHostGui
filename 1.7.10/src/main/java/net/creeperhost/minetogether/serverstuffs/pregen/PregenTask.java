package net.creeperhost.minetogether.serverstuffs.pregen;

import net.creeperhost.minetogether.common.Pair;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import java.util.ArrayList;

public class PregenTask
{
    public boolean preventJoin = true;
    public int dimension;
    public transient ArrayList<Pair<Integer, Integer>> chunksToGen;
    public int chunksPerTick;
    public int storedCurX;
    public int storedCurZ;
    public int minX;
    public int maxX;
    public int minZ;
    public int maxZ;
    public int diameterX = 0;
    public int diameterZ = 0;
    public long startTime = 0;
    public transient long lastCheckedTime = -9001;
    public int chunksDone = 0;
    public int totalChunks = 0;
    public transient int lastChunksDone = 0;
    public transient String lastPregenString = "No status yet!";
    public transient int chunkLoadCount;
    public transient int curChunksPerTick;

    public PregenTask(int dimension, int minX, int maxX, int minZ, int maxZ, int chunksPerTick, boolean preventJoin)
    {
        this.dimension = dimension;
        this.chunksPerTick = chunksPerTick;
        this.minX = minX;
        this.maxX = maxX;
        this.minZ = minZ;
        this.maxZ = maxZ;
        this.storedCurX = minX;
        this.storedCurZ = minZ;
        this.preventJoin = preventJoin;

        init();
    }

    public void init()
    {
        startTime = 0;
        if (chunksToGen != null) return;

        WorldServer world = DimensionManager.getWorld(dimension);
        if (diameterX > 0 && totalChunks == 0) // only the first time
        {
            ChunkCoordinates pos = world.getSpawnPoint();
            minX = (pos.posX << 4) - (diameterX / 2);
            maxX = (pos.posX << 4) + (diameterX / 2);

            minZ = (pos.posZ << 4) - (diameterZ / 2);
            maxZ = (pos.posZ << 4) + (diameterZ / 2);
            storedCurX = minX;
            storedCurZ = minZ;
        }

        chunksDone = 0;
        totalChunks = 0;
        chunkLoadCount = world.getChunkProvider().getLoadedChunkCount();

        ArrayList<Pair<Integer, Integer>> chunks = new ArrayList<Pair<Integer, Integer>>();

        for (int curX = minX; curX <= maxX; curX++)
        {
            if (curX < storedCurX)
                continue;
            ;
            for (int curZ = minZ; curZ <= maxZ; curZ++)
            {
                if (curX == storedCurX && curZ <= storedCurZ)
                    continue;

                chunks.add(new Pair<Integer, Integer>(curX, curZ));
                totalChunks++;
            }
        }

        chunksToGen = chunks;

        curChunksPerTick = chunksPerTick;

    }
}
