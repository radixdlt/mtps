package org.radixdlt.explorer.transactions.model;

import com.radixdlt.client.core.atoms.Atom;

public class AtomData {
    public final Atom[] data;

    private AtomData() {
        data = new Atom[0];
    }

}
