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

package com.radixdlt.consensus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.VerifiedVertex;
import org.junit.Test;

public class BFTConfigurationTest {
	@Test
	public void testGetters() {
		BFTValidatorSet validatorSet = mock(BFTValidatorSet.class);
		VerifiedVertex vertex = mock(VerifiedVertex.class);
		QuorumCertificate qc = mock(QuorumCertificate.class);

		BFTConfiguration bftConfiguration = new BFTConfiguration(validatorSet, vertex, qc);
		assertThat(bftConfiguration.getValidatorSet()).isEqualTo(validatorSet);
		assertThat(bftConfiguration.getGenesisVertex()).isEqualTo(vertex);
		assertThat(bftConfiguration.getGenesisQC()).isEqualTo(qc);
	}
}