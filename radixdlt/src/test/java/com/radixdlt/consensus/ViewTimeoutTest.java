/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.consensus;

import com.google.common.hash.HashCode;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.crypto.HashUtils;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;
import org.radix.serialization.SerializeObject;

import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.ledger.AccumulatorState;

public class ViewTimeoutTest extends SerializeObject<ViewTimeout> {
	public ViewTimeoutTest() {
		super(ViewTimeout.class, ViewTimeoutTest::get);
	}

	private static ViewTimeout get() {
		ECPublicKey pubKey = ECKeyPair.generateNew().getPublicKey();
		BFTNode author = BFTNode.create(pubKey);
		ViewTimeoutData viewTimeoutData = ViewTimeoutData.from(author, 5678L, View.of(1234));
		AccumulatorState accumulatorState = new AccumulatorState(3L, HashUtils.zero256());
		LedgerHeader ledgerHeader = LedgerHeader.create(5678L, View.of(1200), accumulatorState, 123456789L, null);
		BFTHeader proposed = new BFTHeader(View.of(1202), HashUtils.zero256(), ledgerHeader);
		BFTHeader parent = new BFTHeader(View.of(1201), HashUtils.zero256(), ledgerHeader);
		BFTHeader committed = new BFTHeader(View.of(1200), HashUtils.zero256(), ledgerHeader);
		VoteData voteData = new VoteData(proposed, parent, committed);
		QuorumCertificate highestQC = new QuorumCertificate(voteData, new TimestampedECDSASignatures());
		HighQC highQC = HighQC.from(highestQC, highestQC);
		ECDSASignature signature = new ECDSASignature();
		return ViewTimeout.from(viewTimeoutData, highQC, signature);
	}

	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(ViewTimeout.class)
			.withPrefabValues(HashCode.class, HashUtils.random256(), HashUtils.random256())
			.verify();
	}
}
