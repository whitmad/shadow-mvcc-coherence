package com.sixwhits.cohmvcc.index;

import java.util.Map.Entry;

import com.sixwhits.cohmvcc.invocable.VersionCacheBinaryEntryWrapper;
import com.tangosol.util.BinaryEntry;
import com.tangosol.util.Filter;
import com.tangosol.util.filter.EntryFilter;

/**
 * Wrap a filter so that it can be given an entry that looks like
 * the logical cache view.
 * 
 * @author David Whitmarsh <david.whitmarsh@sixwhits.com>
 *
 */
public class FilterWrapper implements EntryFilter {

    protected final Filter delegate;

    /**
     * Constructor.
     * @param delegate the filter to wrap
     */
    public FilterWrapper(final Filter delegate) {
        super();
        this.delegate = delegate;
    }

    @Override
    public boolean evaluate(final Object obj) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public boolean evaluateEntry(@SuppressWarnings("rawtypes") final Entry arg) {
        BinaryEntry entry = (BinaryEntry) arg;
        BinaryEntry wrappedEntry = new VersionCacheBinaryEntryWrapper(entry);
        if (delegate instanceof EntryFilter) {
            return ((EntryFilter) delegate).evaluateEntry(wrappedEntry);
        } else {
            return delegate.evaluate(wrappedEntry.getValue());
        }
    }

}
