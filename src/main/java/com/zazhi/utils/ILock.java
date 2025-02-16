package com.zazhi.utils;

public interface ILock {

    boolean lock(long timeoutSec);

    boolean unlock();
}
