package com.betterchunkloading.config;

import com.cupboard.config.ICommonConfig;
import com.google.gson.JsonObject;

public class CommonConfiguration implements ICommonConfig
{
    public boolean enablePrediction          = true;
    public int     predictiondidstanceoffset = -2;
    public int     predictionarea            = 7;

    public boolean enableLazyChunkloading = true;
    public double  lazyloadingspeed       = 0.6;


    public boolean  enableFasterChunkLoading       = true;
    public boolean  debugLogging       = false;

    public CommonConfiguration()
    {
    }

    public JsonObject serialize()
    {
        final JsonObject root = new JsonObject();

        final JsonObject entry3 = new JsonObject();
        entry3.addProperty("desc:", "Enables predictive chunkloading, which predicts player movement and preloads an area infront: default:true");
        entry3.addProperty("enablePrediction", enablePrediction);
        root.add("enablePrediction", entry3);

        final JsonObject entry = new JsonObject();
        entry.addProperty("desc:", "Offset to the distance(based on simulation distance) at which chunk prediction starts pre-loading: default:-2 chunks");
        entry.addProperty("predictiondidstanceoffset", predictiondidstanceoffset);
        root.add("predictiondidstanceoffset", entry);

        final JsonObject entry2 = new JsonObject();
        entry2.addProperty("desc:", "Size of the area marked for preloading: default:7 chunks, max: 32, min: 2");
        entry2.addProperty("predictionarea", predictionarea);
        root.add("predictionarea", entry2);

        final JsonObject entry5 = new JsonObject();
        entry5.addProperty("desc:",
          "Enables lazy chunkloading around the player, which makes the area loaded directly around the player react more slowly to player position changes.(Improves server performance, less chunks are loaded/unlaoded frequently) : default:true");
        entry5.addProperty("enableLazyChunkloading", enableLazyChunkloading);
        root.add("enableLazyChunkloading", entry5);

        final JsonObject entry6 = new JsonObject();
        entry6.addProperty("desc:",
          "Set the speed of lazy loading, increasing this makes the lazy chunk loading gets less lazy and react to player position changes faster: default:0.6");
        entry6.addProperty("lazyloadingspeed", lazyloadingspeed);
        root.add("lazyloadingspeed", entry6);

        final JsonObject entry7 = new JsonObject();
        entry7.addProperty("desc:", "Enables faster chunk loading, which slightly improves the general chunk loading speed: default:true");
        entry7.addProperty("enableFasterChunkLoading", enableFasterChunkLoading);
        root.add("enableFasterChunkLoading", entry7);

        final JsonObject entry8 = new JsonObject();
        entry8.addProperty("desc:", "Enables debug logging for all features: default:false");
        entry8.addProperty("debugLogging", debugLogging);
        root.add("debugLogging", entry8);

        return root;
    }

    public void deserialize(JsonObject data)
    {
        predictiondidstanceoffset = data.get("predictiondidstanceoffset").getAsJsonObject().get("predictiondidstanceoffset").getAsInt();
        predictionarea = Math.max(2, Math.min(32, data.get("predictionarea").getAsJsonObject().get("predictionarea").getAsInt()));
        enablePrediction = data.get("enablePrediction").getAsJsonObject().get("enablePrediction").getAsBoolean();
        enableLazyChunkloading = data.get("enableLazyChunkloading").getAsJsonObject().get("enableLazyChunkloading").getAsBoolean();
        enableFasterChunkLoading = data.get("enableFasterChunkLoading").getAsJsonObject().get("enableFasterChunkLoading").getAsBoolean();
        lazyloadingspeed = Math.max(0.1, data.get("lazyloadingspeed").getAsJsonObject().get("lazyloadingspeed").getAsDouble());
        debugLogging = data.get("debugLogging").getAsJsonObject().get("debugLogging").getAsBoolean();
    }
}
