/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.ledger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.radixdlt.consensus.Hasher;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.crypto.Hash;
import org.junit.Before;
import org.junit.Test;

public class EpochChangeManagerTest {
	private EpochChangeManager epochChangeManager;
	private EpochChangeSender sender;
	private Hasher hasher;

	@Before
	public void setup() {
		this.sender = mock(EpochChangeSender.class);
		this.hasher = mock(Hasher.class);
		epochChangeManager = new EpochChangeManager(sender, hasher);
	}

	@Test
	public void when_sending_committed_atom_with_epoch_change__then_should_send_epoch_change() {
		long genesisEpoch = 123;
		BFTValidatorSet validatorSet = mock(BFTValidatorSet.class);
		VerifiedCommandsAndProof cmd = mock(VerifiedCommandsAndProof.class);
		VerifiedLedgerHeaderAndProof proof = mock(VerifiedLedgerHeaderAndProof.class);
		when(proof.getEpoch()).thenReturn(genesisEpoch);
		when(proof.isEndOfEpoch()).thenReturn(true);
		when(proof.getStateVersion()).thenReturn(1234L);
		when(cmd.getHeader()).thenReturn(proof);
		when(hasher.hash(any())).thenReturn(mock(Hash.class));

		epochChangeManager.sendCommitted(cmd, validatorSet);

		verify(sender, times(1))
			.epochChange(
				argThat(e -> e.getProof().equals(proof)
					&& e.getBFTConfiguration().getValidatorSet().equals(validatorSet)
					&& e.getEpoch() == 124L
					&& e.getBFTConfiguration().getGenesisVertex().getView().isGenesis()
				)
			);
	}
}