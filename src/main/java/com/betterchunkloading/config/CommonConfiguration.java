package com.betterchunkloading.config;

import com.cupboard.config.ICommonConfig;
import com.google.gson.JsonObject;

public class CommonConfiguration implements ICommonConfig
{
    public static boolean convertWaterSource = false;
    public static boolean convertLavaSource  = false;
    public boolean enablePrediction = true;
    public int     predictionarea   = 7;

    public boolean enableSmartChunkLoading = true;
    public double smartChunkLoadingSpeed = 1.0;
    public double predictionLoadingSpeed = 1.0;

    public boolean enableFasterChunkTasks = false;
    public boolean optimizeWaiting        = true;
    public boolean enableSmartPostProcessing = true;
    public boolean debugLogging              = false;

    public boolean preventWalkUnloaded = true;

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

        final JsonObject entry12 = new JsonObject();
        entry12.addProperty("desc:",
            "Set a modifier to prediction area loading speed, increasing the value increases the speed at which chunks load around the player. Note that faster loading also means higher impact on TPS. range: [0.01 -> 10.0], default: 1.0");
        entry12.addProperty("predictionLoadingSpeed", predictionLoadingSpeed);
        root.add("predictionLoadingSpeed", entry12);

        final JsonObject entry2 = new JsonObject();
        entry2.addProperty("desc:", "Size of the area marked for preloading: default:7 chunks, max: 32, min: 2");
        entry2.addProperty("predictionarea", predictionarea);
        root.add("predictionarea", entry2);

        final JsonObject entry5 = new JsonObject();
        entry5.addProperty("desc:",
            "Enables smart chunkloading around the player, which dynamically loads the around the player : default:true");
        entry5.addProperty("enableSmartChunkLoading", enableSmartChunkLoading);
        root.add("enableSmartChunkLoading", entry5);

        final JsonObject entry13 = new JsonObject();
        entry13.addProperty("desc:",
            "Set a modifier to smart chunkloading speed, increasing the value increases the speed at which chunks load around the player. Note that faster loading also means higher impact on TPS. range: [0.01 -> 10.0], default: 1.0");
        entry13.addProperty("smartChunkLoadingSpeed", smartChunkLoadingSpeed);
        root.add("smartChunkLoadingSpeed", entry13);

        final JsonObject entry11 = new JsonObject();
        entry11.addProperty("desc:",
          "Prevents players from moving into unloaded areas on serverside, which stalls the server and forceloads the chunk: default:true");
        entry11.addProperty("preventWalkUnloaded", preventWalkUnloaded);
        root.add("preventWalkUnloaded", entry11);

        final JsonObject entry7 = new JsonObject();
        entry7.addProperty("desc:", "Enables smart post processing, which slightly improves the general chunk loading speed by waiting with post processing(e.g. fluid updates) until neighbouring chunks are loaded: default:true");
        entry7.addProperty("enableSmartPostProcessing", enableSmartPostProcessing);
        root.add("enableSmartPostProcessing", entry7);

        final JsonObject ENTRY9 = new JsonObject();
        ENTRY9.addProperty("desc:", "Enables faster worldgen tasks: default:false");
        ENTRY9.addProperty("enableFasterChunkTasks", enableFasterChunkTasks);
        root.add("enableFasterChunkTasks", ENTRY9);

        final JsonObject entry09 = new JsonObject();
        entry09.addProperty("desc:", "Optimizes time the world is stalled while waiting for a chunk: default:true");
        entry09.addProperty("optimizeWaiting", optimizeWaiting);
        root.add("optimizeWaiting", entry09);

        final JsonObject entry8 = new JsonObject();
        entry8.addProperty("desc:", "Enables debug logging to show chunk loading changes: default:false");
        entry8.addProperty("debugLogging", debugLogging);
        root.add("debugLogging", entry8);

        return root;
    }

    public void deserialize(JsonObject data)
    {
        predictionarea = Math.max(2, Math.min(32, data.get("predictionarea").getAsJsonObject().get("predictionarea").getAsInt()));
        enablePrediction = data.get("enablePrediction").getAsJsonObject().get("enablePrediction").getAsBoolean();
        enableSmartChunkLoading = data.get("enableSmartChunkLoading").getAsJsonObject().get("enableSmartChunkLoading").getAsBoolean();
        enableSmartPostProcessing = data.get("enableSmartPostProcessing").getAsJsonObject().get("enableSmartPostProcessing").getAsBoolean();
        enableFasterChunkTasks = data.get("enableFasterChunkTasks").getAsJsonObject().get("enableFasterChunkTasks").getAsBoolean();
        preventWalkUnloaded = data.get("preventWalkUnloaded").getAsJsonObject().get("preventWalkUnloaded").getAsBoolean();
        optimizeWaiting = data.get("optimizeWaiting").getAsJsonObject().get("optimizeWaiting").getAsBoolean();
        smartChunkLoadingSpeed = Math.min(10.0, Math.max(0.01, data.get("smartChunkLoadingSpeed").getAsJsonObject().get("smartChunkLoadingSpeed").getAsDouble()));
        predictionLoadingSpeed = Math.min(10.0, Math.max(0.01, data.get("predictionLoadingSpeed").getAsJsonObject().get("predictionLoadingSpeed").getAsDouble()));
        debugLogging = data.get("debugLogging").getAsJsonObject().get("debugLogging").getAsBoolean();
    }
}
