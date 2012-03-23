package com.sixwhits.cohmvcc.testsupport;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.littlegrid.ClusterMemberGroup;
import org.littlegrid.impl.DefaultClusterMemberGroupBuilder;

/**
 * Base class for unit tests using littlegrid. Responsible for setup and teardown
 * of the cluster, and ensuring required system properties are set.
 * 
 * Provides some useful constants
 * 
 * @author David Whitmarsh <david.whitmarsh@sixwhits.com>
 *
 */
public class AbstractLittlegridTest {

    private ClusterMemberGroup cmg;
    protected static final long BASETIME = 40L * 365L * 24L * 60L * 60L * 1000L;
    protected static final String INVOCATIONSERVICENAME = "InvocationService";

    /**
     * initialise system properties.
     */
    @BeforeClass
    public static void setSystemProperties() {
        System.setProperty("pof-config-file", "mvcc-pof-config-test.xml");
        System.setProperty("tangosol.pof.enabled", "true");
        System.setProperty("littlegrid.join.timeout.milliseconds", "100");
        System.setProperty("tangosol.coherence.cachefactorybuilder",
                "com.sixwhits.cohmvcc.monitor.CacheFactoryBuilder");
    }

    /**
     * Set up the cluster.
     */
    @Before
    public void setUpCluster() {
        System.out.println("******setUp");
        DefaultClusterMemberGroupBuilder builder = new DefaultClusterMemberGroupBuilder();
        cmg = builder.setStorageEnabledCount(2).buildAndConfigureForStorageDisabledClient();
    }
    
    /**
     * Get the ClusterMemberGroup.
     * @return the ClusterMemberGroup
     */
    protected ClusterMemberGroup getClusterMemberGroup() {
        return cmg;
    }

    /**
     * shutdown the cluster.
     */
    @After
    public void tearDown() {
        System.out.println("******tearDown");
//        CacheFactory.shutdown();
        cmg.stopAll();
    }

}
