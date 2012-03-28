package com.sixwhits.cohmvcc.cache.internal;

import static com.sixwhits.cohmvcc.domain.IsolationLevel.repeatableRead;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

import com.sixwhits.cohmvcc.domain.SampleDomainObject;
import com.sixwhits.cohmvcc.domain.TransactionId;
import com.sixwhits.cohmvcc.testsupport.AbstractLittlegridTest;
import com.tangosol.util.Filter;
import com.tangosol.util.InvocableMap.EntryProcessor;
import com.tangosol.util.extractor.PofExtractor;
import com.tangosol.util.filter.EqualsFilter;

/**
 * Test that distributed invocation works correctly when a member dies.
 * 
 * @author David Whitmarsh <david.whitmarsh@sixwhits.com>
 *
 */
public class MVCCTransactionalCacheKillMemberTest extends AbstractLittlegridTest {

    private static final String TESTCACHEMAME = "testcache";
    private MVCCTransactionalCacheImpl<Integer, SampleDomainObject> cache;

    /**
     * create cluster and initialise cache.
     */
    @Before
    public void setUp() {
        System.out.println("******initialise cache");
        cache = new MVCCTransactionalCacheImpl<Integer, SampleDomainObject>(TESTCACHEMAME, "InvocationService");
    }

    /**
     * invoke a long-running entryprocessor on all members,
     * then kill a member before it completes. Check that the
     * invocation still produces the correct result
     */
    @Test(timeout = 20000)
    public void testInvokeAllFilter() {
        System.out.println("******InvokeAll(Filter)");

        final TransactionId ts1 = new TransactionId(BASETIME, 0, 0);
        final TransactionId ts2 = new TransactionId(BASETIME + 1, 0, 0);

        SampleDomainObject val1 = new SampleDomainObject(88, "eighty-eight");
        SampleDomainObject val2 = new SampleDomainObject(77, "seventy-seven");

        for (int key = 0; key < 5; key++) {
            cache.insert(ts1, true, key * 2, val1);
            cache.insert(ts1, true, key * 2 + 1, val2);
        }

        Filter filter = new EqualsFilter(new PofExtractor(null, SampleDomainObject.POF_INTV), 77);
        EntryProcessor ep = new LongWaitingEntryProcessor();

        asynchMemberKill(1);

        Set<Integer> keySet = cache.invokeAll(ts2, repeatableRead, true, filter, ep).getResultMap().keySet();

        Set<Integer> expected = new HashSet<Integer>(5);
        expected.add(1);
        expected.add(3);
        expected.add(5);
        expected.add(7);
        expected.add(9);

        assertEquals(5, keySet.size());
        assertTrue(keySet.containsAll(expected));

        for (Integer key : expected) {
            assertEquals(val2, cache.get(ts2, repeatableRead, key));
        }

    }


    /**
     * Spawn a thread to kill a member.  
     * @param memberId the member to kill
     */
    private void asynchMemberKill(final int memberId) {
        new Thread(new Runnable() {

            @Override
            public void run() {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    System.out.println("interrupted");
                }
                getClusterMemberGroup().getClusterMember(memberId).stop();
            }
        }).start();
    }

}
