package com.sixwhits.cohmvcc.pof;

import java.io.IOException;
import java.util.Collection;
import java.util.TreeSet;

import com.tangosol.io.pof.PofReader;
import com.tangosol.io.pof.PofWriter;
import com.tangosol.io.pof.reflect.Codec;

/**
 * Codec to ensure a collection is instantiated as a {@code SortedSet} on deserialisation.
 * 
 * @author David Whitmarsh <david.whitmarsh@sixwhits.com>
 *
 */
public class SortedSetCodec implements Codec {

    @SuppressWarnings("rawtypes")
    @Override
    public Object decode(final PofReader pofreader, final int i) throws IOException {
        return pofreader.readCollection(i, new TreeSet());
    }

    @SuppressWarnings("rawtypes")
    @Override
    public void encode(final PofWriter pofwriter, final int i, final Object obj)
            throws IOException {
        pofwriter.writeCollection(i, (Collection) obj);
    }

}