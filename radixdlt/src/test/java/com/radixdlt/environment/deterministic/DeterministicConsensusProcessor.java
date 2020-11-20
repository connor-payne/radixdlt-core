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

package com.radixdlt.environment.deterministic;

import com.google.inject.Inject;
import com.radixdlt.consensus.BFTEventProcessor;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.ViewTimeout;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTSyncRequestProcessor;
import com.radixdlt.consensus.bft.BFTUpdate;
import com.radixdlt.consensus.epoch.LocalTimeout;
import com.radixdlt.consensus.sync.BFTSync;
import com.radixdlt.consensus.sync.GetVerticesErrorResponse;
import com.radixdlt.consensus.sync.GetVerticesRequest;
import com.radixdlt.consensus.sync.GetVerticesResponse;
import com.radixdlt.consensus.sync.LocalGetVerticesRequest;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.ledger.LedgerUpdate;
import java.util.Objects;
import java.util.Set;

/**
 * Consensus only (no epochs) deterministic consensus processor
 */
public class DeterministicConsensusProcessor implements DeterministicMessageProcessor {
	private final BFTEventProcessor bftEventProcessor;
	private final BFTSync vertexStoreSync;
	private final BFTSyncRequestProcessor requestProcessor;
	private final Set<EventProcessor<BFTUpdate>> bftUpdateProcessors;

	@Inject
	public DeterministicConsensusProcessor(
		BFTEventProcessor bftEventProcessor,
		BFTSync vertexStoreSync,
		BFTSyncRequestProcessor requestProcessor,
		Set<EventProcessor<BFTUpdate>> bftUpdateProcessors
	) {
		this.bftEventProcessor = Objects.requireNonNull(bftEventProcessor);
		this.vertexStoreSync = Objects.requireNonNull(vertexStoreSync);
		this.requestProcessor = Objects.requireNonNull(requestProcessor);
		this.bftUpdateProcessors = Objects.requireNonNull(bftUpdateProcessors);
	}

	@Override
	public void start() {
		this.bftEventProcessor.start();
	}

	@Override
	public void handleMessage(BFTNode origin, Object message) {
		if (message instanceof LocalTimeout) {
			bftEventProcessor.processLocalTimeout(((LocalTimeout) message).getView());
		} else if (message instanceof ViewTimeout) {
			bftEventProcessor.processViewTimeout((ViewTimeout) message);
		} else if (message instanceof Proposal) {
			bftEventProcessor.processProposal((Proposal) message);
		} else if (message instanceof Vote) {
			bftEventProcessor.processVote((Vote) message);
		} else if (message instanceof GetVerticesRequest) {
			requestProcessor.processGetVerticesRequest((GetVerticesRequest) message);
		} else if (message instanceof GetVerticesResponse) {
			vertexStoreSync.processGetVerticesResponse((GetVerticesResponse) message);
		} else if (message instanceof GetVerticesErrorResponse) {
			vertexStoreSync.processGetVerticesErrorResponse((GetVerticesErrorResponse) message);
		} else if (message instanceof BFTUpdate) {
			bftUpdateProcessors.forEach(p -> p.process((BFTUpdate) message));
		} else if (message instanceof LedgerUpdate) {
			vertexStoreSync.processLedgerUpdate((LedgerUpdate) message);
		} else if (message instanceof LocalGetVerticesRequest) {
			vertexStoreSync.processGetVerticesLocalTimeout((LocalGetVerticesRequest) message);
		} else {
			throw new IllegalArgumentException("Unknown message type: " + message.getClass().getName());
		}
	}
}
