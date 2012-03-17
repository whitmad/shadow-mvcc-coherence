package com.sixwhits.cohmvcc.transaction.internal;

import java.util.Collection;

import com.sixwhits.cohmvcc.cache.CacheName;
import com.sixwhits.cohmvcc.domain.IsolationLevel;
import com.sixwhits.cohmvcc.domain.TransactionId;
import com.sixwhits.cohmvcc.transaction.Transaction;
import com.sixwhits.cohmvcc.transaction.TransactionNotificationListener;
import com.tangosol.util.Filter;

/**
 * Read-only transaction.
 * 
 * @author David Whitmarsh <david.whitmarsh@sixwhits.com>
 *
 */
public class ReadOnlyTransaction implements Transaction {

    private final TransactionId transactionId;
    private final IsolationLevel isolationLevel;
    private final TransactionNotificationListener notificationListener;


    /**
     * @param transactionId transaction id
     * @param isolationLevel isolation level
     * @param notificationListener notification listener
     */
    public ReadOnlyTransaction(final TransactionId transactionId, 
            final IsolationLevel isolationLevel, 
            final TransactionNotificationListener notificationListener) {
        super();
        this.transactionId = transactionId;
        this.isolationLevel = isolationLevel;
        this.notificationListener = notificationListener;
    }

    @Override
    public TransactionId getTransactionId() {
        return transactionId;
    }

    @Override
    public IsolationLevel getIsolationLevel() {
        return isolationLevel;
    }

    @Override
    public boolean isAutoCommit() {
        return false;
    }

    @Override
    public void addKeyAffected(final CacheName cacheName, final Object key) {
        throw new UnsupportedOperationException("read only transaction");
    }

    @Override
    public void addKeySetAffected(final CacheName cacheName, final Collection<Object> keys) {
        throw new UnsupportedOperationException("read only transaction");
    }

    @Override
    public int addFilterAffected(final CacheName cacheName, final Filter filter) {
        throw new UnsupportedOperationException("read only transaction");
    }

    @Override
    public void filterKeysAffected(final int invocationId, final Collection<?> keys) {
        throw new UnsupportedOperationException("read only transaction");
    }

    @Override
    public void filterPartitionsAffected(final int invocationId, 
            final Collection<Integer> keys) {
        throw new UnsupportedOperationException("read only transaction");
    }

    @Override
    public void setRollbackOnly() {
        throw new UnsupportedOperationException("read only transaction");
    }

    @Override
    public void commit() {
        notificationListener.transactionComplete(this);
    }

    @Override
    public void rollback() {
        throw new UnsupportedOperationException("read only transaction");
    }

}
