package com.netflix.niws.loadbalancer;

import static org.easymock.EasyMock.expect;
import static org.junit.Assert.*;
import static org.powermock.api.easymock.PowerMock.createMock;
import static org.powermock.api.easymock.PowerMock.replay;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.configuration.Configuration;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.easymock.PowerMock;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import com.google.common.collect.Lists;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.client.config.ClientConfigBuilder;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.config.IClientConfigKey.CommonKeys;
import com.netflix.config.ConfigurationManager;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.discovery.DiscoveryManager;
import com.netflix.loadbalancer.AvailabilityFilteringRule;
import com.netflix.loadbalancer.BaseLoadBalancer;
import com.netflix.loadbalancer.DummyPing;
import com.netflix.loadbalancer.DynamicServerListLoadBalancer;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.IRule;
import com.netflix.loadbalancer.LoadBalancerBuilder;
import com.netflix.loadbalancer.RoundRobinRule;
import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerList;
import com.netflix.loadbalancer.ServerListFilter;
import com.netflix.loadbalancer.ZoneAffinityServerListFilter;
import com.netflix.loadbalancer.ZoneAwareLoadBalancer;

@RunWith(PowerMockRunner.class)
@PrepareForTest( {DiscoveryManager.class, DiscoveryClient.class} )
@PowerMockIgnore({"javax.management.*", "com.sun.jersey.*", "com.sun.*", "org.apache.*", "weblogic.*", "com.netflix.config.*", "com.sun.jndi.dns.*",
    "javax.naming.*", "com.netflix.logging.*", "javax.ws.*"})
public class LBBuilderTest {
    
    static Server expected = new Server("www.example.com", 8001);
    
    static List<InstanceInfo> getDummyInstanceInfo(String appName, String host, int port){
        List<InstanceInfo> list = new ArrayList<InstanceInfo>();
        InstanceInfo info = InstanceInfo.Builder.newBuilder().setAppName(appName)
                .setHostName(host)
                .setPort(port)
                .build();
        list.add(info);
        return list;
    }
    
    static class NiwsClientConfig extends DefaultClientConfigImpl {
        public NiwsClientConfig() {
            super();
        }

        @Override
        public String getNameSpace() {
            return "niws.client";
        }
    }
    
    @Before
    public void setupMock(){
        List<InstanceInfo> instances = getDummyInstanceInfo("dummy", expected.getHost(), expected.getPort());
        PowerMock.mockStatic(DiscoveryManager.class);
        PowerMock.mockStatic(DiscoveryClient.class);

        DiscoveryClient mockedDiscoveryClient = createMock(DiscoveryClient.class);
        DiscoveryManager mockedDiscoveryManager = createMock(DiscoveryManager.class);

        expect(DiscoveryClient.getZone((InstanceInfo) EasyMock.anyObject())).andReturn("dummyZone").anyTimes();
        expect(DiscoveryManager.getInstance()).andReturn(mockedDiscoveryManager).anyTimes();
        expect(mockedDiscoveryManager.getDiscoveryClient()).andReturn(mockedDiscoveryClient).anyTimes();


        expect(mockedDiscoveryClient.getInstancesByVipAddress("dummy:7001", false, null)).andReturn(instances).anyTimes();

        replay(DiscoveryManager.class);
        replay(DiscoveryClient.class);
        replay(mockedDiscoveryManager);
        replay(mockedDiscoveryClient);
    }

    @Test
    public void testBuildWithDiscoveryEnabledNIWSServerList() {
        IRule rule = new AvailabilityFilteringRule();
        ServerList<DiscoveryEnabledServer> list = new DiscoveryEnabledNIWSServerList("dummy:7001");
        ServerListFilter<DiscoveryEnabledServer> filter = new ZoneAffinityServerListFilter<DiscoveryEnabledServer>();
        ZoneAwareLoadBalancer<DiscoveryEnabledServer> lb = LoadBalancerBuilder.<DiscoveryEnabledServer>newBuilder()
                .withDynamicServerList(list)
                .withRule(rule)
                .withServerListFilter(filter)
                .buildDynamicServerListLoadBalancer();
        assertNotNull(lb);
        assertEquals(Lists.newArrayList(expected), lb.getServerList(false));
        assertSame(filter, lb.getFilter());
        assertSame(list, lb.getServerListImpl());
    }

    @Test
    public void testBuildWithArchaiusProperties() {
        Configuration config = ConfigurationManager.getConfigInstance();
        config.setProperty("client1.niws.client." + CommonKeys.DeploymentContextBasedVipAddresses, "dummy:7001");
        config.setProperty("client1.niws.client." + CommonKeys.InitializeNFLoadBalancer, "true");
        config.setProperty("client1.niws.client." + CommonKeys.NFLoadBalancerClassName, DynamicServerListLoadBalancer.class.getName());
        config.setProperty("client1.niws.client." + CommonKeys.NFLoadBalancerRuleClassName, RoundRobinRule.class.getName());
        config.setProperty("client1.niws.client." + CommonKeys.NIWSServerListClassName, DiscoveryEnabledNIWSServerList.class.getName());
        config.setProperty("client1.niws.client." + CommonKeys.NIWSServerListFilterClassName, ZoneAffinityServerListFilter.class.getName());
        IClientConfig clientConfig = ClientConfigBuilder.newBuilderWithArchaiusProperties(NiwsClientConfig.class, "client1").build();
        ILoadBalancer lb = LoadBalancerBuilder.newBuilder().withClientConfig(clientConfig).buildLoadBalancerFromConfigWithReflection();
        assertNotNull(lb);
        assertEquals(DynamicServerListLoadBalancer.class.getName(), lb.getClass().getName());
        DynamicServerListLoadBalancer<Server> dynamicLB = (DynamicServerListLoadBalancer<Server>) lb;
        assertTrue(dynamicLB.getFilter() instanceof ZoneAffinityServerListFilter);
        assertTrue(dynamicLB.getRule() instanceof RoundRobinRule);
        assertTrue(dynamicLB.getPing() instanceof DummyPing);
        assertEquals(Lists.newArrayList(expected), lb.getServerList(false));
    }

    @Test
    public void testBuildStaticServerListLoadBalancer() {
        List<Server> list = Lists.newArrayList(expected, expected);
        IRule rule = new AvailabilityFilteringRule();
        IClientConfig clientConfig = ClientConfigBuilder.newBuilderWithDefaultConfigValues()
                .withMaxAutoRetriesNextServer(3).build();

        assertEquals(3, clientConfig.getPropertyWithType(CommonKeys.MaxAutoRetriesNextServer).intValue());
        BaseLoadBalancer lb = LoadBalancerBuilder.newBuilder()
                .withRule(rule)
                .buildFixedServerListLoadBalancer(list);
        assertEquals(list, lb.getServerList(false));
        assertSame(rule, lb.getRule());
    }
}