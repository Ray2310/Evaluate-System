package com.hmdp.utils;

/***
 * 分布式锁的获取和释放
 *
 */
public interface ILock {

    boolean tryLock(long timeOutSec);
    void unLock();

}
