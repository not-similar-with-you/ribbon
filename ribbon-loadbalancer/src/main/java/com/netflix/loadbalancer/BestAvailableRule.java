/*
 *
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.netflix.loadbalancer;

import java.util.List;

/**
 * A rule that skips servers with "tripped" circuit breaker and picks the
 * server with lowest concurrent requests.
 * <p>
 * This rule should typically work with {@link ServerListSubsetFilter} which puts a limit on the 
 * servers that is visible to the rule. This ensure that it only needs to find the minimal 
 * concurrent requests among a small number of servers. Also, each client will get a random list of 
 * servers which avoids the problem that one server with the lowest concurrent requests is 
 * chosen by a large number of clients and immediately gets overwhelmed.
 * 
 * @author awang
 *
 * 选取最少并发量请求的服务器
 *
 */
public class BestAvailableRule extends ClientConfigEnabledRoundRobinRule {

    private LoadBalancerStats loadBalancerStats;
    
    @Override
    public Server choose(Object key) {
        // 统计对象 是否为 null
        if (loadBalancerStats == null) {
            // 使用父类 轮训
            return super.choose(key);
        }
        // 获取所有的服务器列表
        List<Server> serverList = getLoadBalancer().getAllServers();
        int minimalConcurrentConnections = Integer.MAX_VALUE;
        // 当前 毫秒
        long currentTime = System.currentTimeMillis();
        Server chosen = null;
        for (Server server: serverList) {
            // 获取各个服务器的状态
            ServerStats serverStats = loadBalancerStats.getSingleServerStat(server);
            // 没有触发断路器的话继续执行
            if (!serverStats.isCircuitBreakerTripped(currentTime)) {
                // 获取当前服务器的请求个数
                int concurrentConnections = serverStats.getActiveRequestsCount(currentTime);
                // 比较各个服务器之间的请求数，然后选取请求数最少的服务器并放到chosen变量中
                if (concurrentConnections < minimalConcurrentConnections) {
                    minimalConcurrentConnections = concurrentConnections;
                    chosen = server;
                }
            }
        }
        // 如果没有选上，调用父类ClientConfigEnabledRoundRobinRule的choose方法，也就是使用RoundRobinRule轮询的方式进行负载均衡
        if (chosen == null) {
            return super.choose(key);
        } else {
            return chosen;
        }
    }

    @Override
    public void setLoadBalancer(ILoadBalancer lb) {
        super.setLoadBalancer(lb);
        if (lb instanceof AbstractLoadBalancer) {
            loadBalancerStats = ((AbstractLoadBalancer) lb).getLoadBalancerStats();            
        }
    }
    
    

}
