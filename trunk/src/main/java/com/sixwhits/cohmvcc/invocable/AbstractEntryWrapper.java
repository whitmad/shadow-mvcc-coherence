package com.sixwhits.cohmvcc.invocable;

import com.sixwhits.cohmvcc.cache.CacheName;
import com.tangosol.io.Serializer;
import com.tangosol.net.BackingMapContext;
import com.tangosol.net.BackingMapManagerContext;
import com.tangosol.util.Binary;
import com.tangosol.util.BinaryEntry;
import com.tangosol.util.ObservableMap;
import com.tangosol.util.ValueExtractor;
import com.tangosol.util.extractor.PofExtractor;

/**
 * Abstract base class for entry wrappers that translate the physical cache
 * view where the previous value is a separate cache entry, to the logical view
 * allowing access to that previous value.
 * 
 * @author David Whitmarsh <david.whitmarsh@sixwhits.com>
 *
 */
public abstract class AbstractEntryWrapper implements EntryWrapper {

    private final BinaryEntry parentEntry;
    private final BinaryEntry priorBinaryEntry;
    private boolean priorRead = false;
    private CacheName cacheName;

    /**
     * Constructor.
     * @param parentEntry parent BinaryEntry from the key cache
     * @param priorBinaryEntry binary entry for prior version
     * @param cacheName name of the current cache
     */
    public AbstractEntryWrapper(final BinaryEntry parentEntry, final BinaryEntry priorBinaryEntry,
            final CacheName cacheName) {
        super();
        this.parentEntry = parentEntry;
        this.priorBinaryEntry = priorBinaryEntry;
        this.cacheName = cacheName;
    }

    /**
     * @return the backing map context for the current caches
     */
    private BackingMapContext getVersionCacheBackingMapContext() {
        return parentEntry.getBackingMapContext().getManagerContext().getBackingMapContext(
                cacheName.getVersionCacheName());
    }

    @Override
    public Object extract(final ValueExtractor valueextractor) {
        if (valueextractor instanceof PofExtractor) {
            return ((PofExtractor) valueextractor).extractFromEntry(this);
        } else {
            return valueextractor.extract(getValue());
        }
    }

    @Override
    public Object getKey() {
        return parentEntry.getKey();
    }

    /**
     * @return the binary entry from the version cache for the previous version
     */
    private BinaryEntry getPriorBinaryEntry() {
        priorRead = true;
        return priorBinaryEntry;
    }

    @Override
    public Binary getOriginalBinaryValue() {

        BinaryEntry priorEntry = getPriorBinaryEntry();
        Binary result = null;

        if (priorEntry != null) {
            result = priorEntry.getOriginalBinaryValue();
        }

        return result;
    }

    @Override
    public boolean isPresent() {
        BinaryEntry priorEntry = getPriorBinaryEntry();
        return (priorEntry != null);
    }

    @Override
    public Binary getBinaryKey() {
        return parentEntry.getBinaryKey();
    }

    @Override
    public Serializer getSerializer() {
        return parentEntry.getSerializer();
    }

    @Override
    public BackingMapManagerContext getContext() {
        return getBackingMapContext().getManagerContext();
    }

    @Override
    public Object getOriginalValue() {
        return getContext().getValueFromInternalConverter().convert(getOriginalBinaryValue());
    }

    @Override
    public ObservableMap getBackingMap() {
        return getBackingMapContext().getBackingMap();
    }

    @Override
    public BackingMapContext getBackingMapContext() {
        return getVersionCacheBackingMapContext();
    }

    @Override
    public void expire(final long l) {
        throw new UnsupportedOperationException("expiry of MVCC cache entries not supported");
    }

    /**
     * @return true if the previous version has been read
     */
    public boolean isPriorRead() {
        return priorRead;
    }

}