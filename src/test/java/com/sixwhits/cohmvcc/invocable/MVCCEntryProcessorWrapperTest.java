package com.sixwhits.cohmvcc.invocable;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;

import junit.framework.Assert;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;

import com.sixwhits.cohmvcc.cache.CacheName;
import com.sixwhits.cohmvcc.domain.DeletedObject;
import com.sixwhits.cohmvcc.domain.IsolationLevel;
import com.sixwhits.cohmvcc.domain.ProcessorResult;
import com.sixwhits.cohmvcc.domain.SampleDomainObject;
import com.sixwhits.cohmvcc.domain.TransactionId;
import com.sixwhits.cohmvcc.domain.TransactionSetWrapper;
import com.sixwhits.cohmvcc.domain.VersionedKey;
import com.sixwhits.cohmvcc.exception.FutureReadException;
import com.sixwhits.cohmvcc.index.MVCCExtractor;
import com.sixwhits.cohmvcc.testsupport.AbstractLittlegridTest;
import com.tangosol.io.pof.PortableException;
import com.tangosol.net.CacheFactory;
import com.tangosol.net.NamedCache;
import com.tangosol.util.InvocableMap.EntryProcessor;
import com.tangosol.util.extractor.IdentityExtractor;
import com.tangosol.util.extractor.PofExtractor;
import com.tangosol.util.filter.AlwaysFilter;
import com.tangosol.util.filter.EqualsFilter;
import com.tangosol.util.processor.ConditionalPut;
import com.tangosol.util.processor.ConditionalRemove;
import com.tangosol.util.processor.ExtractorProcessor;

/**
 * Test the MVCCEntryProcessorWrapper by making EntryProcessor invocations to excercise
 * all the behaviours of the EntryProcessor and associated BinaryEntry wrappers.
 * 
 * @author David Whitmarsh <david.whitmarsh@sixwhits.com>
 *
 */
public class MVCCEntryProcessorWrapperTest extends AbstractLittlegridTest {

    private static final CacheName CACHENAME = new CacheName("testcache");
    private NamedCache versionCache;
    private NamedCache keyCache;

    /**
     * create cluster and initialise cache.
     */
    @Before
    public void setUp() {
        versionCache = CacheFactory.getCache(CACHENAME.getVersionCacheName());
        versionCache.addIndex(new MVCCExtractor(), false, null);
        keyCache = CacheFactory.getCache(CACHENAME.getKeyCacheName());
    }

    /**
     * Test inserting from an EntryProcessor.
     */
    @Test
    public void testInsert() {

        TransactionId ts = new TransactionId(BASETIME, 0, 0);

        EntryProcessor insertProcessor = new ConditionalPut(AlwaysFilter.INSTANCE, "a test value");
        EntryProcessor wrapper = new MVCCEntryProcessorWrapper<String, Object>(
                ts, insertProcessor, IsolationLevel.serializable, true, CACHENAME);

        keyCache.invoke(99, wrapper);

        assertEquals(versionCache.size(), 1);
        @SuppressWarnings("unchecked")
        Map.Entry<VersionedKey<Integer>, String> versionCachEntry 
            = (Entry<VersionedKey<Integer>, String>) versionCache.entrySet().iterator().next();
        assertEquals(new VersionedKey<Integer>(99, ts), versionCachEntry.getKey());
        assertTrue((Boolean) versionCache.invoke(
                versionCachEntry.getKey(), DecorationExtractorProcessor.COMMITTED_INSTANCE));
        assertFalse((Boolean) versionCache.invoke(
                versionCachEntry.getKey(), DecorationExtractorProcessor.DELETED_INSTANCE));
        assertEquals("a test value", versionCachEntry.getValue());

    }
    
    /**
     * Test using a PofExtractor.
     */
    @Test
    public void testPofExtract() {

        TransactionId ts = new TransactionId(BASETIME, 0, 0);

        SampleDomainObject sdo = new SampleDomainObject(77, "a test value");

        EntryProcessor insertProcessor = new ConditionalPut(AlwaysFilter.INSTANCE, sdo);
        EntryProcessor wrapper = new MVCCEntryProcessorWrapper<SampleDomainObject, Object>(
                ts, insertProcessor, IsolationLevel.serializable, true, CACHENAME);

        keyCache.invoke(99, wrapper);

        Assert.assertEquals(versionCache.size(), 1);
        @SuppressWarnings("unchecked")
        Map.Entry<VersionedKey<Integer>, String> versionCachEntry
            = (Entry<VersionedKey<Integer>, String>) versionCache.entrySet().iterator().next();
        Assert.assertEquals(new VersionedKey<Integer>(99, ts), versionCachEntry.getKey());
        assertTrue((Boolean) versionCache.invoke(
                versionCachEntry.getKey(), DecorationExtractorProcessor.COMMITTED_INSTANCE));
        assertFalse((Boolean) versionCache.invoke(
                versionCachEntry.getKey(), DecorationExtractorProcessor.DELETED_INSTANCE));
        assertEquals(sdo, versionCachEntry.getValue());

        TransactionId ts2 = new TransactionId(BASETIME + 125, 0, 0);
        EntryProcessor extractProcessor = new ExtractorProcessor(new PofExtractor(null, SampleDomainObject.POF_INTV));
        EntryProcessor wrapper2 = new MVCCEntryProcessorWrapper<SampleDomainObject, Integer>(
                ts2, extractProcessor, IsolationLevel.serializable, true, CACHENAME);
        @SuppressWarnings("unchecked")
        ProcessorResult<Integer, Integer> pr =  (ProcessorResult<Integer, Integer>) keyCache.invoke(99, wrapper2);
        Assert.assertEquals(77, pr.getResult().intValue());

        Collection<TransactionId> readSet = getReadTransactions(99);
        Assert.assertEquals(1, readSet.size());
        MatcherAssert.assertThat(readSet, Matchers.hasItem(ts2));
    }

    /**
     * Test updating (create new version).
     */
    @Test
    public void testConditionalUpdate() {

        TransactionId ts1 = new TransactionId(BASETIME, 0, 0);

        EntryProcessor insertProcessor = new ConditionalPut(AlwaysFilter.INSTANCE, "version 1");
        EntryProcessor wrapper = new MVCCEntryProcessorWrapper<String, Object>(
                ts1, insertProcessor, IsolationLevel.serializable, true, CACHENAME);
        keyCache.invoke(99, wrapper);

        TransactionId ts2 = new TransactionId(BASETIME + 60000, 0, 0);
        EntryProcessor updateProcessor = new ConditionalPut(
                new EqualsFilter(new IdentityExtractor(), "version 1"), "version 2");
        EntryProcessor wrapper2 = new MVCCEntryProcessorWrapper<String, Object>(
                ts2, updateProcessor, IsolationLevel.serializable, true, CACHENAME);
        keyCache.invoke(99, wrapper2);

        Assert.assertEquals(2, versionCache.size());

        VersionedKey<Integer> key1 = new VersionedKey<Integer>(99, ts1);
        assertEquals("version 1", versionCache.get(key1));
        assertTrue((Boolean) versionCache.invoke(key1, DecorationExtractorProcessor.COMMITTED_INSTANCE));
        assertFalse((Boolean) versionCache.invoke(key1, DecorationExtractorProcessor.DELETED_INSTANCE));

        VersionedKey<Integer> key2 = new VersionedKey<Integer>(99, ts2);
        assertEquals("version 2", versionCache.get(key2));
        assertTrue((Boolean) versionCache.invoke(key2, DecorationExtractorProcessor.COMMITTED_INSTANCE));
        assertFalse((Boolean) versionCache.invoke(key2, DecorationExtractorProcessor.DELETED_INSTANCE));

        Assert.assertEquals(keyCache.size(), 1);

        Collection<TransactionId> readSet = getReadTransactions(99);
        Assert.assertEquals(1, readSet.size());
        MatcherAssert.assertThat(readSet, Matchers.hasItem(ts2));

    }

    /**
     * Update a deleted entry.
     */
    @Test
    public void testConditionalUpdateOnDeleted() {

        TransactionId ts1 = new TransactionId(BASETIME, 0, 0);

        EntryProcessor insertProcessor = new ConditionalPut(AlwaysFilter.INSTANCE, "version 1");
        EntryProcessor wrapper = new MVCCEntryProcessorWrapper<String, Object>(
                ts1, insertProcessor, IsolationLevel.serializable, true, CACHENAME);
        keyCache.invoke(99, wrapper);

        TransactionId ts2 = new TransactionId(BASETIME + 30000, 0, 0);
        EntryProcessor removeProcessor = new ConditionalRemove(new EqualsFilter(new IdentityExtractor(), "version 1"));
        EntryProcessor wrapper2 = new MVCCEntryProcessorWrapper<String, Object>(
                ts2, removeProcessor, IsolationLevel.serializable, true, CACHENAME);
        keyCache.invoke(99, wrapper2);

        TransactionId ts3 = new TransactionId(BASETIME + 60000, 0, 0);
        EntryProcessor updateProcessor = new ConditionalPut(
                new EqualsFilter(new IdentityExtractor(), "version 1"), "version 2");
        EntryProcessor wrapper3 = new MVCCEntryProcessorWrapper<String, Object>(
                ts3, updateProcessor, IsolationLevel.serializable, true, CACHENAME);
        keyCache.invoke(99, wrapper3);

        Assert.assertEquals(2, versionCache.size());

        VersionedKey<Integer> key1 = new VersionedKey<Integer>(99, ts1);
        assertEquals("version 1", versionCache.get(key1));
        assertTrue((Boolean) versionCache.invoke(key1, DecorationExtractorProcessor.COMMITTED_INSTANCE));
        assertFalse((Boolean) versionCache.invoke(key1, DecorationExtractorProcessor.DELETED_INSTANCE));

        VersionedKey<Integer> key2 = new VersionedKey<Integer>(99, ts2);
        assertEquals(DeletedObject.INSTANCE, versionCache.get(key2));
        assertTrue((Boolean) versionCache.invoke(key2, DecorationExtractorProcessor.COMMITTED_INSTANCE));
        assertTrue((Boolean) versionCache.invoke(key2, DecorationExtractorProcessor.DELETED_INSTANCE));

        Assert.assertEquals(keyCache.size(), 1);

        Collection<TransactionId> readSet = getReadTransactions(99);
        Assert.assertEquals(2, readSet.size());
        MatcherAssert.assertThat(readSet, Matchers.hasItem(ts2));
        MatcherAssert.assertThat(readSet, Matchers.hasItem(ts3));

    }

    /**
     * @param key entry key
     * @return the read markers for the entry
     */
    private Collection<TransactionId> getReadTransactions(final int key) {
        TransactionSetWrapper tsw = (TransactionSetWrapper) keyCache.get(key);
        return tsw == null ? null : tsw.getTransactionIdSet();
    }

    /**
     * Test conditional delete.
     */
    @Test
    public void testConditionalDelete() {

        TransactionId ts1 = new TransactionId(BASETIME, 0, 0);

        EntryProcessor insertProcessor = new ConditionalPut(AlwaysFilter.INSTANCE, "version 1");
        EntryProcessor wrapper = new MVCCEntryProcessorWrapper<String, Object>(
                ts1, insertProcessor, IsolationLevel.serializable, true, CACHENAME);
        keyCache.invoke(99, wrapper);

        TransactionId ts2 = new TransactionId(BASETIME + 60000, 0, 0);
        EntryProcessor removeProcessor = new ConditionalRemove(new EqualsFilter(new IdentityExtractor(), "version 1"));
        EntryProcessor wrapper2 = new MVCCEntryProcessorWrapper<String, Object>(
                ts2, removeProcessor, IsolationLevel.serializable, true, CACHENAME);
        keyCache.invoke(99, wrapper2);

        Assert.assertEquals(2, versionCache.size());

        VersionedKey<Integer> key1 = new VersionedKey<Integer>(99, ts1);
        assertEquals("version 1", versionCache.get(key1));
        assertTrue((Boolean) versionCache.invoke(key1, DecorationExtractorProcessor.COMMITTED_INSTANCE));
        assertFalse((Boolean) versionCache.invoke(key1, DecorationExtractorProcessor.DELETED_INSTANCE));

        VersionedKey<Integer> key2 = new VersionedKey<Integer>(99, ts2);
        assertEquals(DeletedObject.INSTANCE, versionCache.get(key2));
        assertTrue((Boolean) versionCache.invoke(key2, DecorationExtractorProcessor.COMMITTED_INSTANCE));
        assertTrue((Boolean) versionCache.invoke(key2, DecorationExtractorProcessor.DELETED_INSTANCE));

        Assert.assertEquals(keyCache.size(), 1);

        Collection<TransactionId> readSet = getReadTransactions(99);
        Assert.assertEquals(1, readSet.size());
        MatcherAssert.assertThat(readSet, Matchers.hasItem(ts2));

    }

    /**
     * Conditional update where the condition is not met.
     */
    @Test
    public void testConditionalNonUpdate() {

        TransactionId ts1 = new TransactionId(BASETIME, 0, 0);

        EntryProcessor insertProcessor = new ConditionalPut(AlwaysFilter.INSTANCE, "version 1");
        EntryProcessor wrapper = new MVCCEntryProcessorWrapper<String, Object>(
                ts1, insertProcessor, IsolationLevel.serializable, true, CACHENAME);
        keyCache.invoke(99, wrapper);

        TransactionId ts2 = new TransactionId(BASETIME + 60000, 0, 0);
        EntryProcessor updateProcessor = new ConditionalPut(
                new EqualsFilter(new IdentityExtractor(), "version 0"), "version 2");
        EntryProcessor wrapper2 = new MVCCEntryProcessorWrapper<String, Object>(
                ts2, updateProcessor, IsolationLevel.serializable, true, CACHENAME);
        keyCache.invoke(99, wrapper2);

        Assert.assertEquals(1, versionCache.size());

        VersionedKey<Integer> key1 = new VersionedKey<Integer>(99, ts1);
        assertEquals("version 1", versionCache.get(key1));
        assertTrue((Boolean) versionCache.invoke(key1, DecorationExtractorProcessor.COMMITTED_INSTANCE));
        assertFalse((Boolean) versionCache.invoke(key1, DecorationExtractorProcessor.DELETED_INSTANCE));

        Assert.assertEquals(keyCache.size(), 1);

        Collection<TransactionId> readSet = getReadTransactions(99);
        Assert.assertEquals(1, readSet.size());
        MatcherAssert.assertThat(readSet, Matchers.hasItem(ts2));

    }

    /**
     * Check that we get an uncommitted result when updating an uncommitted row.
     */
    public void testUpdateOnUncommitted() {

        TransactionId ts1 = new TransactionId(BASETIME, 0, 0);

        EntryProcessor insertProcessor = new ConditionalPut(AlwaysFilter.INSTANCE, "version 1");
        EntryProcessor wrapper = new MVCCEntryProcessorWrapper<String, Object>(
                ts1, insertProcessor, IsolationLevel.serializable, false, CACHENAME);
        keyCache.invoke(99, wrapper);

        TransactionId ts2 = new TransactionId(BASETIME + 60000, 0, 0);
        EntryProcessor updateProcessor = new ConditionalPut(
                new EqualsFilter(new IdentityExtractor(), "version 1"), "version 2");
        EntryProcessor wrapper2 = new MVCCEntryProcessorWrapper<String, Object>(
                ts2, updateProcessor, IsolationLevel.serializable, false, CACHENAME);

        @SuppressWarnings("unchecked")
        ProcessorResult<Integer, Object> pr = (ProcessorResult<Integer, Object>) keyCache.invoke(99, wrapper2);

        assertTrue(pr.isUncommitted());

    }

    /**
     * Check that we get a FutureReadException when updating earlier than already read.
     * @throws Throwable expect a FutureReadException
     */
    @Test(expected = FutureReadException.class)
    public void testWriteEarlierThanRead() throws Throwable {

        TransactionId ts2 = new TransactionId(BASETIME + 60000, 0, 0);
        EntryProcessor updateProcessor = new ConditionalPut(
                new EqualsFilter(new IdentityExtractor(), "version 0"), "version 2");
        EntryProcessor wrapper2 = new MVCCEntryProcessorWrapper<String, Object>(
                ts2, updateProcessor, IsolationLevel.serializable, true, CACHENAME);
        keyCache.invoke(99, wrapper2);

        TransactionId ts1 = new TransactionId(BASETIME, 0, 0);

        EntryProcessor insertProcessor = new ConditionalPut(AlwaysFilter.INSTANCE, "version 1");
        EntryProcessor wrapper = new MVCCEntryProcessorWrapper<String, Object>(
                ts1, insertProcessor, IsolationLevel.serializable, true, CACHENAME);
        try {
            keyCache.invoke(99, wrapper);
        } catch (PortableException ex) {
            System.out.println(ex);
            throw ex.getCause();
        }

    }
}