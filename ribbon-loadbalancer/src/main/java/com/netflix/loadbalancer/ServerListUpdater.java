package com.netflix.loadbalancer;

/**
 * strategy for {@link com.netflix.loadbalancer.DynamicServerListLoadBalancer} to use for different ways
 * of doing dynamic server list updates.
 *
 * @author David Liu
 * 触发向Eureka Server去获取服务实例清单以及 在获取到服务实例清单后更新本地的服务实例清单
 */
public interface ServerListUpdater {

    /**
     * an interface for the updateAction that actually executes a server list update
     */
    public interface UpdateAction {
        void doUpdate();
    }


    /**启动服务更新器，传入的UpdateAction对象为更新操作的具体实现
     * start the serverList updater with the given update action
     * This call should be idempotent.
     *
     * @param updateAction
     */
    void start(UpdateAction updateAction);

    /**停止服务更新器
     * stop the serverList updater. This call should be idempotent
     */
    void stop();

    /**获取最近的更新时间戳
     * @return the last update timestamp as a {@link java.util.Date} string
     */
    String getLastUpdate();

    /**获取上一次更新到现在的时间间隔，单位为毫秒
     * @return the number of ms that has elapsed since last update
     */
    long getDurationSinceLastUpdateMs();

    /**获取错过的更新周期数
     * @return the number of update cycles missed, if valid
     */
    int getNumberMissedCycles();

    /** 获取核心线程数
     * @return the number of threads used, if vaid
     */
    int getCoreThreads();
}
