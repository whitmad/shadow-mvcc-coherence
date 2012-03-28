package com.sixwhits.cohmvcc.domain;

import com.tangosol.io.pof.annotation.Portable;
import com.tangosol.io.pof.annotation.PortableProperty;

/**
 * Encapsulate the result of a single {@code EntryProcessor} invocation. This
 * may be the actual return value of a wrapped {@code EntryProcessor}, or the
 * version cache key of an uncommitted entry that prevented completion.
 * 
 * @author David Whitmarsh <david.whitmarsh@sixwhits.com>
 *
 * @param <K> the cache logical key type
 * @param <R> the wrapped {@code EntryProcessor} result type
 */
@Portable
public class ProcessorResult<K, R> {

    @PortableProperty(0) private R result;
    @PortableProperty(1) private VersionedKey<K> waitKey;
    @PortableProperty(1) private boolean changed;
    @PortableProperty(2) private boolean returnResult;

    /**
     *  Default constructor for POF use only.
     */
    public ProcessorResult() {
        super();
    }

    /**
     * Constructor for a result indicating a wait is required for an uncommitted entry.
     * @param waitKey the version cache key of an uncommitted entry
     */
    public ProcessorResult(final VersionedKey<K> waitKey) {
        super();
        this.result = null;
        this.waitKey = waitKey;
        this.changed = false;
        this.returnResult = false;
    }
    /**
     * Constructor for a successful invocation.
     * @param result the {@code EntryProcessor} result
     * @param changed true if the processor invocation created a new version
     * @param returnResult false if this entry was not included in the result map from {@code processAll}
     */
    public ProcessorResult(final R result,
            final boolean changed, final boolean returnResult) {
        super();
        this.result = result;
        this.waitKey = null;
        this.changed = changed;
        this.returnResult = returnResult;
    }

    /**
     * @return the {@code EntryProcessor} result or null
     * if processing could not proceed because of an uncommitted entry
     */
    public R getResult() {
        return result;
    }

    /**
     * Does this result represent a wait for an uncommitted entry?
     * @return true if this result represents an uncommitted entry
     */
    public boolean isUncommitted() {
        return waitKey != null;
    }

    /**
     * Return the uncommitted entry key that we must wait for.
     * @return the version cache key that prevented execution or null
     */
    public VersionedKey<K> getWaitKey() {
        return waitKey;
    }

    /**
     * Did invocation create a new version.
     * @return true if a new version was created
     */
    public boolean isChanged() {
        return changed;
    }

    /**
     * Should the result be returned to the caller?
     * @return true if the result should be returned to the caller
     */
    public boolean isReturnResult() {
        return returnResult;
    }

}