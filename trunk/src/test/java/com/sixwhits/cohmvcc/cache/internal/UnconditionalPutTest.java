package com.sixwhits.cohmvcc.cache.internal;

import static com.sixwhits.cohmvcc.domain.IsolationLevel.readCommitted;
import static junit.framework.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

import com.sixwhits.cohmvcc.cache.CacheName;
import com.sixwhits.cohmvcc.domain.SampleDomainObject;
import com.sixwhits.cohmvcc.domain.TransactionId;
import com.sixwhits.cohmvcc.domain.VersionedKey;
import com.sixwhits.cohmvcc.invocable.MVCCEntryProcessorWrapper;
import com.sixwhits.cohmvcc.testsupport.AbstractLittlegridTest;
import com.tangosol.net.CacheFactory;
import com.tangosol.net.NamedCache;

/**
 * Test the UnconditionalPutProcessor.
 * 
 * @author David Whitmarsh <david.whitmarsh@sixwhits.com>
 *
 */
public class UnconditionalPutTest extends AbstractLittlegridTest {

    private static final String TESTCACHEMAME = "testcache";

    /**
     * create cluster and initialise cache.
     */
    @Before
    public void setUp() {
        System.out.println("******initialise cache");
        new MVCCTransactionalCacheImpl<Integer, SampleDomainObject>(TESTCACHEMAME, "InvocationService");
    }

    /**
     * Do the test.
     */
    @Test
    public void testPut() {

        final TransactionId ts = new TransactionId(BASETIME, 0, 0);
        Integer theKey = 99;
        SampleDomainObject theValue = new SampleDomainObject(88, "eighty-eight");

        CacheName cacheName = new CacheName(TESTCACHEMAME);
        NamedCache keyCache = CacheFactory.getCache(cacheName.getKeyCacheName());
        NamedCache versionCache = CacheFactory.getCache(cacheName.getVersionCacheName());

        keyCache.invoke(theKey, 
                new MVCCEntryProcessorWrapper<Integer, String>(
                        ts, new UnconditionalPutProcessor(theValue, true), readCommitted, true, cacheName));

        assertEquals(theValue, versionCache.get(new VersionedKey<Integer>(99, ts)));

    }

}