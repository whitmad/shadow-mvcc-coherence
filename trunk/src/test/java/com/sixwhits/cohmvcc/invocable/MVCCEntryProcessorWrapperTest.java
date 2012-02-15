package com.sixwhits.cohmvcc.invocable;

import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;

import junit.framework.Assert;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.littlegrid.coherence.testsupport.ClusterMemberGroup;
import org.littlegrid.coherence.testsupport.SystemPropertyConst;
import org.littlegrid.coherence.testsupport.impl.DefaultClusterMemberGroupBuilder;

import com.sixwhits.cohmvcc.domain.IsolationLevel;
import com.sixwhits.cohmvcc.domain.SampleDomainObject;
import com.sixwhits.cohmvcc.domain.TransactionId;
import com.sixwhits.cohmvcc.domain.TransactionSetWrapper;
import com.sixwhits.cohmvcc.domain.TransactionalValue;
import com.sixwhits.cohmvcc.domain.VersionedKey;
import com.sixwhits.cohmvcc.exception.FutureReadException;
import com.sixwhits.cohmvcc.exception.UncommittedReadException;
import com.sixwhits.cohmvcc.index.MVCCExtractor;
import com.sixwhits.cohmvcc.invocable.MVCCEntryProcessorWrapper;
import com.tangosol.io.pof.ConfigurablePofContext;
import com.tangosol.io.pof.PofContext;
import com.tangosol.io.pof.PortableException;
import com.tangosol.net.CacheFactory;
import com.tangosol.net.NamedCache;
import com.tangosol.util.ExternalizableHelper;
import com.tangosol.util.InvocableMap.EntryProcessor;
import com.tangosol.util.extractor.IdentityExtractor;
import com.tangosol.util.extractor.PofExtractor;
import com.tangosol.util.filter.AlwaysFilter;
import com.tangosol.util.filter.EqualsFilter;
import com.tangosol.util.processor.ConditionalPut;
import com.tangosol.util.processor.ConditionalRemove;
import com.tangosol.util.processor.ExtractorProcessor;

public class MVCCEntryProcessorWrapperTest {
	
	private ClusterMemberGroup cmg;
	private static final String VERSIONCACHENAME = "testcache-ver";
	private static final String KEYCACHENAME = "testcache-key";
	private static final long BASETIME = 40L*365L*24L*60L*60L*1000L;
	private NamedCache versionCache;
	private NamedCache keyCache;
	private PofContext pofContext = new ConfigurablePofContext("mvcc-pof-config-test.xml");

	@BeforeClass
	public static void setSystemProperties() {
		System.setProperty("tangosol.pof.enabled", "true");
		System.setProperty("pof-config-file", "mvcc-pof-config-test.xml");
	}
	
	@Before
	public void setUp() throws Exception {
		System.setProperty("tangosol.pof.enabled", "true");
		DefaultClusterMemberGroupBuilder builder = new DefaultClusterMemberGroupBuilder();
		cmg = builder.setStorageEnabledCount(1).build();

		System.setProperty(SystemPropertyConst.DISTRIBUTED_LOCAL_STORAGE_KEY, "false");
		versionCache = CacheFactory.getCache(VERSIONCACHENAME);
		versionCache.addIndex(new MVCCExtractor(), false, null);
		keyCache = CacheFactory.getCache(KEYCACHENAME);
	}
	
	@Test
	public void testInsert() {
		
		TransactionId ts = new TransactionId(BASETIME, 0, 0);
		
		EntryProcessor insertProcessor = new ConditionalPut(AlwaysFilter.INSTANCE, "a test value");
		EntryProcessor wrapper = new MVCCEntryProcessorWrapper<String>(ts, insertProcessor, IsolationLevel.serializable, true, VERSIONCACHENAME);
		
		keyCache.invoke(99, wrapper);
		
		Assert.assertEquals(versionCache.size(), 1);
		@SuppressWarnings("unchecked")
		Map.Entry<VersionedKey<Integer>, TransactionalValue> versionCachEntry = (Entry<VersionedKey<Integer>, TransactionalValue>) versionCache.entrySet().iterator().next();
		Assert.assertEquals(new VersionedKey<Integer>(99, ts), versionCachEntry.getKey());
		Assert.assertEquals(new TransactionalValue(true, false, ExternalizableHelper.toBinary("a test value", pofContext)), versionCachEntry.getValue());

	}
	@Test
	public void testPofExtract() {
		
		TransactionId ts = new TransactionId(BASETIME, 0, 0);
		
		SampleDomainObject sdo = new SampleDomainObject(77, "a test value");
		
		EntryProcessor insertProcessor = new ConditionalPut(AlwaysFilter.INSTANCE, sdo);
		EntryProcessor wrapper = new MVCCEntryProcessorWrapper<SampleDomainObject>(ts, insertProcessor, IsolationLevel.serializable, true, VERSIONCACHENAME);
		
		keyCache.invoke(99, wrapper);
		
		Assert.assertEquals(versionCache.size(), 1);
		@SuppressWarnings("unchecked")
		Map.Entry<VersionedKey<Integer>, TransactionalValue> versionCachEntry = (Entry<VersionedKey<Integer>, TransactionalValue>) versionCache.entrySet().iterator().next();
		Assert.assertEquals(new VersionedKey<Integer>(99, ts), versionCachEntry.getKey());
		Assert.assertEquals(new TransactionalValue(true, false, ExternalizableHelper.toBinary(sdo, pofContext)), versionCachEntry.getValue());
		
		TransactionId ts2 = new TransactionId(BASETIME + 125, 0, 0);
		EntryProcessor extractProcessor = new ExtractorProcessor(new PofExtractor(null, SampleDomainObject.POF_INTV));
		EntryProcessor wrapper2 = new MVCCEntryProcessorWrapper<SampleDomainObject>(ts2, extractProcessor, IsolationLevel.serializable, true, VERSIONCACHENAME);
		int extracted = (Integer) keyCache.invoke(99, wrapper2);
		Assert.assertEquals(77, extracted);

		Collection<TransactionId> readSet = getReadTransactions(99);
		Assert.assertEquals(1, readSet.size());
		MatcherAssert.assertThat(readSet, Matchers.hasItem(ts2));
	}
	
	@Test
	public void testConditionalUpdate() {
		
		TransactionId ts1 = new TransactionId(BASETIME, 0, 0);
		
		EntryProcessor insertProcessor = new ConditionalPut(AlwaysFilter.INSTANCE, "version 1");
		EntryProcessor wrapper = new MVCCEntryProcessorWrapper<String>(ts1, insertProcessor, IsolationLevel.serializable, true, VERSIONCACHENAME);
		keyCache.invoke(99, wrapper);
		
		TransactionId ts2 = new TransactionId(BASETIME + 60000, 0, 0);
		EntryProcessor updateProcessor = new ConditionalPut(new EqualsFilter(new IdentityExtractor(), "version 1"), "version 2");
		EntryProcessor wrapper2 = new MVCCEntryProcessorWrapper<String>(ts2, updateProcessor, IsolationLevel.serializable, true, VERSIONCACHENAME);
		keyCache.invoke(99, wrapper2);
		
		Assert.assertEquals(2, versionCache.size());
		
		Assert.assertEquals(new TransactionalValue(true, false, ExternalizableHelper.toBinary("version 1", pofContext)), versionCache.get(new VersionedKey<Integer>(99, ts1)));
		Assert.assertEquals(new TransactionalValue(true, false, ExternalizableHelper.toBinary("version 2", pofContext)), versionCache.get(new VersionedKey<Integer>(99, ts2)));

		Assert.assertEquals(keyCache.size(), 1);
		
		Collection<TransactionId> readSet = getReadTransactions(99);
		Assert.assertEquals(1, readSet.size());
		MatcherAssert.assertThat(readSet, Matchers.hasItem(ts2));
		
	}

	@Test
	public void testConditionalUpdateOnDeleted() {
		
		TransactionId ts1 = new TransactionId(BASETIME, 0, 0);
		
		EntryProcessor insertProcessor = new ConditionalPut(AlwaysFilter.INSTANCE, "version 1");
		EntryProcessor wrapper = new MVCCEntryProcessorWrapper<String>(ts1, insertProcessor, IsolationLevel.serializable, true, VERSIONCACHENAME);
		keyCache.invoke(99, wrapper);
		
		TransactionId ts2 = new TransactionId(BASETIME + 30000, 0, 0);
		EntryProcessor removeProcessor = new ConditionalRemove(new EqualsFilter(new IdentityExtractor(), "version 1"));
		EntryProcessor wrapper2 = new MVCCEntryProcessorWrapper<String>(ts2, removeProcessor, IsolationLevel.serializable, true, VERSIONCACHENAME);
		keyCache.invoke(99, wrapper2);

		TransactionId ts3 = new TransactionId(BASETIME + 60000, 0, 0);
		EntryProcessor updateProcessor = new ConditionalPut(new EqualsFilter(new IdentityExtractor(), "version 1"), "version 2");
		EntryProcessor wrapper3 = new MVCCEntryProcessorWrapper<String>(ts3, updateProcessor, IsolationLevel.serializable, true, VERSIONCACHENAME);
		keyCache.invoke(99, wrapper3);
		
		Assert.assertEquals(2, versionCache.size());
		
		Assert.assertEquals(new TransactionalValue(true, false, ExternalizableHelper.toBinary("version 1", pofContext)), versionCache.get(new VersionedKey<Integer>(99, ts1)));
		Assert.assertEquals(new TransactionalValue(true, true), versionCache.get(new VersionedKey<Integer>(99, ts2)));

		Assert.assertEquals(keyCache.size(), 1);
		
		Collection<TransactionId> readSet = getReadTransactions(99);
		Assert.assertEquals(2, readSet.size());
		MatcherAssert.assertThat(readSet, Matchers.hasItem(ts2));
		MatcherAssert.assertThat(readSet, Matchers.hasItem(ts3));
		
	}

	private Collection<TransactionId> getReadTransactions(int key) {
		TransactionSetWrapper tsw = (TransactionSetWrapper) keyCache.get(key);
		return tsw == null ? null : tsw.getTransactionIdSet();
	}
	
	@Test
	public void testConditionalDelete() {
		
		TransactionId ts1 = new TransactionId(BASETIME, 0, 0);
		
		EntryProcessor insertProcessor = new ConditionalPut(AlwaysFilter.INSTANCE, "version 1");
		EntryProcessor wrapper = new MVCCEntryProcessorWrapper<String>(ts1, insertProcessor, IsolationLevel.serializable, true, VERSIONCACHENAME);
		keyCache.invoke(99, wrapper);
		
		TransactionId ts2 = new TransactionId(BASETIME + 60000, 0, 0);
		EntryProcessor removeProcessor = new ConditionalRemove(new EqualsFilter(new IdentityExtractor(), "version 1"));
		EntryProcessor wrapper2 = new MVCCEntryProcessorWrapper<String>(ts2, removeProcessor, IsolationLevel.serializable, true, VERSIONCACHENAME);
		keyCache.invoke(99, wrapper2);
		
		Assert.assertEquals(2, versionCache.size());
		
		Assert.assertEquals(new TransactionalValue(true, false, ExternalizableHelper.toBinary("version 1", pofContext)), versionCache.get(new VersionedKey<Integer>(99, ts1)));
		Assert.assertEquals(new TransactionalValue(true, true), versionCache.get(new VersionedKey<Integer>(99, ts2)));

		Assert.assertEquals(keyCache.size(), 1);

		Collection<TransactionId> readSet = getReadTransactions(99);
		Assert.assertEquals(1, readSet.size());
		MatcherAssert.assertThat(readSet, Matchers.hasItem(ts2));
		
	}

	@Test

	public void testConditionalNonUpdate() {
		
		TransactionId ts1 = new TransactionId(BASETIME, 0, 0);
		
		EntryProcessor insertProcessor = new ConditionalPut(AlwaysFilter.INSTANCE, "version 1");
		EntryProcessor wrapper = new MVCCEntryProcessorWrapper<String>(ts1, insertProcessor, IsolationLevel.serializable, true, VERSIONCACHENAME);
		keyCache.invoke(99, wrapper);
		
		TransactionId ts2 = new TransactionId(BASETIME + 60000, 0, 0);
		EntryProcessor updateProcessor = new ConditionalPut(new EqualsFilter(new IdentityExtractor(), "version 0"), "version 2");
		EntryProcessor wrapper2 = new MVCCEntryProcessorWrapper<String>(ts2, updateProcessor, IsolationLevel.serializable, true, VERSIONCACHENAME);
		keyCache.invoke(99, wrapper2);
		
		Assert.assertEquals(1, versionCache.size());
		
		Assert.assertEquals(new TransactionalValue(true, false, ExternalizableHelper.toBinary("version 1", pofContext)), versionCache.get(new VersionedKey<Integer>(99, ts1)));

		Assert.assertEquals(keyCache.size(), 1);

		Collection<TransactionId> readSet = getReadTransactions(99);
		Assert.assertEquals(1, readSet.size());
		MatcherAssert.assertThat(readSet, Matchers.hasItem(ts2));
		
	}
	
	@Test(expected = UncommittedReadException.class)
	public void testUpdateOnUncommitted() throws Throwable {
		
		TransactionId ts1 = new TransactionId(BASETIME, 0, 0);
		
		EntryProcessor insertProcessor = new ConditionalPut(AlwaysFilter.INSTANCE, "version 1");
		EntryProcessor wrapper = new MVCCEntryProcessorWrapper<String>(ts1, insertProcessor, IsolationLevel.serializable, false, VERSIONCACHENAME);
		keyCache.invoke(99, wrapper);
		
		TransactionId ts2 = new TransactionId(BASETIME + 60000, 0, 0);
		EntryProcessor updateProcessor = new ConditionalPut(new EqualsFilter(new IdentityExtractor(), "version 1"), "version 2");
		EntryProcessor wrapper2 = new MVCCEntryProcessorWrapper<String>(ts2, updateProcessor, IsolationLevel.serializable, false, VERSIONCACHENAME);
		try {
			keyCache.invoke(99, wrapper2);
		} catch (PortableException ex) {
			System.out.println(ex);
			throw ex.getCause();
		}
		
	}

	@Test(expected = FutureReadException.class)
	public void testWriteEarlierThanRead() throws Throwable {
		
		TransactionId ts2 = new TransactionId(BASETIME + 60000, 0, 0);
		EntryProcessor updateProcessor = new ConditionalPut(new EqualsFilter(new IdentityExtractor(), "version 0"), "version 2");
		EntryProcessor wrapper2 = new MVCCEntryProcessorWrapper<String>(ts2, updateProcessor, IsolationLevel.serializable, true, VERSIONCACHENAME);
		keyCache.invoke(99, wrapper2);
		
		TransactionId ts1 = new TransactionId(BASETIME, 0, 0);
		
		EntryProcessor insertProcessor = new ConditionalPut(AlwaysFilter.INSTANCE, "version 1");
		EntryProcessor wrapper = new MVCCEntryProcessorWrapper<String>(ts1, insertProcessor, IsolationLevel.serializable, true, VERSIONCACHENAME);
		try {
			keyCache.invoke(99, wrapper);
		} catch (PortableException ex) {
			System.out.println(ex);
			throw ex.getCause();
		}
		
	}

	@After
	public void tearDown() throws Exception {
		cmg.shutdownAll();
	}

	
}