package com.sixwhits.cohmvcc.integration;

import static com.sixwhits.cohmvcc.domain.IsolationLevel.readCommitted;
import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;

import com.sixwhits.cohmvcc.cache.CacheName;
import com.sixwhits.cohmvcc.testsupport.AbstractLittlegridTest;
import com.sixwhits.cohmvcc.transaction.ManagerIdSource;
import com.sixwhits.cohmvcc.transaction.SystemTimestampSource;
import com.sixwhits.cohmvcc.transaction.ThreadTransactionManager;
import com.sixwhits.cohmvcc.transaction.TimestampSource;
import com.sixwhits.cohmvcc.transaction.TransactionManager;
import com.sixwhits.cohmvcc.transaction.internal.ManagerIdSourceImpl;
import com.sixwhits.cohmvcc.transaction.internal.TransactionCache;
import com.sixwhits.cohmvcc.transaction.internal.TransactionCacheImpl;
import com.tangosol.net.CacheFactory;
import com.tangosol.net.NamedCache;

/**
 * Simple integration test to pull all the components together.
 * 
 * @author David Whitmarsh <david.whitmarsh@sixwhits.com>
 *
 */
public class IntegrationTest extends AbstractLittlegridTest {
    
    private TransactionManager transactionManager;
    
    /**
     * Set up the tm.
     */
    @Before
    public void initialiseTransactionManager() {
        
        TimestampSource timestampSource = new SystemTimestampSource();
        ManagerIdSource managerIdSource = new ManagerIdSourceImpl();
        TransactionCache transactionCache = new TransactionCacheImpl(INVOCATIONSERVICENAME);
        
        transactionManager = new ThreadTransactionManager(
                timestampSource, managerIdSource, INVOCATIONSERVICENAME, transactionCache, false, false, readCommitted);
        

    }
    /**
     * Get the cache and run a couple of transactions.
     */
    @Test
    public void testCompleteTransaction() {
        
        NamedCache cache = transactionManager.getCache("test-cache1");
        
        cache.put(1, "version 1");
        
        transactionManager.getTransaction().commit();
        
        cache.put(1, "version 2");

        transactionManager.getTransaction().commit();
        
        CacheName cacheName = new CacheName(cache.getCacheName()); 
        
        Assert.assertEquals(2, CacheFactory.getCache(cacheName.getVersionCacheName()).size());
    }

}
