package org.radixdlt.explorer.helper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.util.Base58;

import static org.assertj.core.api.Assertions.assertThat;

public class BitcoinAddressHelperTest {

    // BEGIN: Happy path
    @Test
    public void when_correct_magic_and_bitcoin_address_is_given_valid_radix_address_is_returned() {
        BitcoinAddressHelper helper = new BitcoinAddressHelper();
        RadixAddress address = helper.getRadixAddress(240909314, "1dice1e6pdhLzzWQq7yMidf6j8eAg7pkY");
        assertThat(address).isEqualTo(new RadixAddress("JHBT6xyH2LbYFhoyjMtN8JehiR3jMpskGxWnhS1ePs58BuVdbkg"));
    }
    // END: Happy path


}
