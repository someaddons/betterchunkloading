package com.betterchunkloading.config;

import com.cupboard.config.ICommonConfig;
import com.google.gson.JsonObject;

public class CommonConfiguration implements ICommonConfig
{
    public boolean enablePrediction          = true;
    public int     predictionarea            = 7;
    public boolean     enablePreGen            = true;
    public int     preGenArea            = 7;

    public boolean enableLazyChunkloading = true;
    public double  lazyloadingspeed       = 0.7;

    public boolean  enableFasterChunkTasks       = true;
    public boolean  enableFasterChunkLoading       = true;
    public boolean  debugLogging       = false;

    public CommonConfiguration()
    {
    }

    public JsonObject serialize()
    {
        final JsonObject root = new JsonObject();

        final JsonObject entry3 = new JsonObject();
        entry3.addProperty("desc:", "Enables predictive chunkloading, which predicts player movement and preloads an area in movement direction: default:true");
        entry3.addProperty("enablePrediction", enablePrediction);
        root.add("enablePrediction", entry3);

        final JsonObject entry2 = new JsonObject();
        entry2.addProperty("desc:", "Size of the area marked for preloading: default:7 chunks, max: 32, min: 2");
        entry2.addProperty("predictionarea", predictionarea);
        root.add("predictionarea", entry2);

        final JsonObject entry9 = new JsonObject();
        entry9.addProperty("desc:", "Enables predictive, async pre-gen far ahead of the player, to generate nonexisting chunks early so they load in time, requires enablePrediction: default:true");
        entry9.addProperty("enablePreGen", enablePreGen);
        root.add("enablePreGen", entry9);

        final JsonObject entry10 = new JsonObject();
        entry10.addProperty("desc:", "Size of the area marked for pregeneration: default:7 chunks, min 1, max 32");
        entry10.addProperty("preGenArea", preGenArea);
        root.add("preGenArea", entry10);

        final JsonObject entry5 = new JsonObject();
        entry5.addProperty("desc:",
          "Enables lazy chunkloading around the player, which makes the area loaded directly around the player react more slowly to player position changes.(Improves server performance, less chunks are loaded/unlaoded frequently) : default:true");
        entry5.addProperty("enableLazyChunkloading", enableLazyChunkloading);
        root.add("enableLazyChunkloading", entry5);

        final JsonObject entry6 = new JsonObject();
        entry6.addProperty("desc:",
          "Set the speed of lazy loading, increasing this makes the lazy chunk loading gets less lazy and react to player position changes faster: default:0.7");
        entry6.addProperty("lazyloadingspeed", lazyloadingspeed);
        root.add("lazyloadingspeed", entry6);

        final JsonObject entry7 = new JsonObject();
        entry7.addProperty("desc:", "Enables faster chunk loading, which slightly improves the general chunk loading speed: default:true");
        entry7.addProperty("enableFasterChunkLoading", enableFasterChunkLoading);
        root.add("enableFasterChunkLoading", entry7);

        final JsonObject entry8 = new JsonObject();
        entry8.addProperty("desc:", "Enables debug logging to show chunk loading changes: default:false");
        entry8.addProperty("debugLogging", debugLogging);
        root.add("debugLogging", entry8);

        final JsonObject ENTRY9 = new JsonObject();
        ENTRY9.addProperty("desc:", "Enables faster chunk tasks: default:true");
        ENTRY9.addProperty("enableFasterChunkTasks", enableFasterChunkTasks);
        root.add("enableFasterChunkTasks", ENTRY9);

        return root;
    }

    public void deserialize(JsonObject data)
    {
        predictionarea = Math.max(2, Math.min(32, data.get("predictionarea").getAsJsonObject().get("predictionarea").getAsInt()));
        preGenArea = Math.max(1, Math.min(32, data.get("preGenArea").getAsJsonObject().get("preGenArea").getAsInt()));
        enablePrediction = data.get("enablePrediction").getAsJsonObject().get("enablePrediction").getAsBoolean();
        enableLazyChunkloading = data.get("enableLazyChunkloading").getAsJsonObject().get("enableLazyChunkloading").getAsBoolean();
        enableFasterChunkLoading = data.get("enableFasterChunkLoading").getAsJsonObject().get("enableFasterChunkLoading").getAsBoolean();
        enableFasterChunkTasks = data.get("enableFasterChunkTasks").getAsJsonObject().get("enableFasterChunkTasks").getAsBoolean();
        enablePreGen = data.get("enablePreGen").getAsJsonObject().get("enablePreGen").getAsBoolean();
        lazyloadingspeed = Math.max(0.1, data.get("lazyloadingspeed").getAsJsonObject().get("lazyloadingspeed").getAsDouble());
        debugLogging = data.get("debugLogging").getAsJsonObject().get("debugLogging").getAsBoolean();
    }
}
