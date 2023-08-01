package com.template.config;

import com.cupboard.config.ICommonConfig;
import com.google.gson.JsonObject;
import com.template.TemplateMod;

public class CommonConfiguration implements ICommonConfig
{

    public boolean skipWeatherOnSleep = false;

    public CommonConfiguration()
    {
    }

    public JsonObject serialize()
    {
        final JsonObject root = new JsonObject();

        final JsonObject entry = new JsonObject();
        entry.addProperty("desc:", "Whether to skip weather after sleeping: default:false");
        entry.addProperty("skipWeatherOnSleep", skipWeatherOnSleep);
        root.add("skipWeatherOnSleep", entry);

        return root;
    }

    public void deserialize(JsonObject data)
    {
        skipWeatherOnSleep = data.get("skipWeatherOnSleep").getAsJsonObject().get("skipWeatherOnSleep").getAsBoolean();
    }
}
