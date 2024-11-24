package com.betterchunkloading.event;

public interface ITickingTask
{
    public boolean tick();

    public void cancel();
}
