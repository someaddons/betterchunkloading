package com.betterchunkloading.config;

import com.cupboard.config.ICommonConfig;
import com.google.gson.JsonObject;

public class CommonConfiguration implements ICommonConfig
{

    public int predictiondistance = 10;

    public CommonConfiguration()
    {
    }

    public JsonObject serialize()
    {
        final JsonObject root = new JsonObject();

        final JsonObject entry = new JsonObject();
        entry.addProperty("desc:", "The distance at which chunk prediction starts pre-loading: default:10 chunks");
        entry.addProperty("predictiondistance", predictiondistance);
        root.add("predictiondistance", entry);

        return root;
    }

    public void deserialize(JsonObject data)
    {
        predictiondistance = data.get("predictiondistance").getAsJsonObject().get("predictiondistance").getAsInt();
    }
}
