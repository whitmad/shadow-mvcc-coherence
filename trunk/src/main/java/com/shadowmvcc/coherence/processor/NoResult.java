/*

Copyright 2012 Shadowmist Ltd.

This file is part of Shadow MVCC for Oracle Coherence.

Shadow MVCC for Oracle Coherence is free software: you can redistribute 
it and/or modify it under the terms of the GNU General Public License 
as published by the Free Software Foundation, either version 3 of the 
License, or (at your option) any later version.

Shadow MVCC for Oracle Coherence is distributed in the hope that it 
will be useful, but WITHOUT ANY WARRANTY; without even the implied 
warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See 
the GNU General Public License for more details.
                        
You should have received a copy of the GNU General Public License
along with Shadow MVCC for Oracle Coherence.  If not, see 
<http://www.gnu.org/licenses/>.

*/

package com.shadowmvcc.coherence.processor;

/**
 * Special result type to indicate that no result should be returned from an EntryProcessor
 * invocation. The key will be omitted from the processAll result map
 * 
 * @author David Whitmarsh <david.whitmarsh@sixwhits.com>
 *
 */
public final class NoResult {
    
    /**
     * the singleton instance.
     */
    public static final NoResult INSTANCE = new NoResult();
    
    /**
     * Private constructor.
     */
    private NoResult() {
    }

}
