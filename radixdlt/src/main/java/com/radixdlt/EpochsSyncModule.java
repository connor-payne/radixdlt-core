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

package com.radixdlt;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.radixdlt.consensus.BFTConfiguration;
import com.radixdlt.consensus.Ledger;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.ProcessWithSyncRunner;
import com.radixdlt.environment.RemoteEventDispatcher;
import com.radixdlt.environment.RemoteEventProcessor;
import com.radixdlt.epochs.EpochsLedgerUpdate;
import com.radixdlt.epochs.EpochsLocalSyncServiceProcessor;
import com.radixdlt.epochs.EpochsRemoteSyncResponseProcessor;
import com.radixdlt.epochs.SyncedEpochSender;
import com.radixdlt.ledger.AccumulatorState;
import com.radixdlt.ledger.DtoCommandsAndProof;
import com.radixdlt.ledger.DtoLedgerHeaderAndProof;
import com.radixdlt.ledger.VerifiedCommandsAndProof;
import com.radixdlt.sync.LocalSyncRequest;
import com.radixdlt.sync.LocalSyncServiceAccumulatorProcessor;
import com.radixdlt.sync.LocalSyncServiceAccumulatorProcessor.SyncTimeoutScheduler;
import com.radixdlt.sync.RemoteSyncResponseValidatorSetVerifier;
import com.radixdlt.sync.RemoteSyncResponseValidatorSetVerifier.InvalidValidatorSetSender;
import com.radixdlt.sync.RemoteSyncResponseValidatorSetVerifier.VerifiedValidatorSetSender;
import com.radixdlt.sync.LocalSyncServiceProcessor;
import com.radixdlt.sync.SyncPatienceMillis;
import java.util.Comparator;
import java.util.function.Function;

/**
 * Epoch+Sync extension
 */
public class EpochsSyncModule extends AbstractModule {
	@Override
	public void configure() {
		bind(LocalSyncServiceProcessor.class).to(EpochsLocalSyncServiceProcessor.class);
		bind(EpochsRemoteSyncResponseProcessor.class).in(Scopes.SINGLETON);
		bind(EpochsLocalSyncServiceProcessor.class).in(Scopes.SINGLETON);
	}

	@ProvidesIntoSet
	@ProcessWithSyncRunner
	private EventProcessor<EpochsLedgerUpdate> epochsLedgerUpdateEventProcessor(EpochsLocalSyncServiceProcessor epochsLocalSyncServiceProcessor) {
		return epochsLocalSyncServiceProcessor.epochsLedgerUpdateEventProcessor();
	}

	@ProvidesIntoSet
	@ProcessWithSyncRunner
	private EventProcessor<EpochsLedgerUpdate> epochsLedgerUpdateEventProcessor(EpochsRemoteSyncResponseProcessor epochsRemoteSyncResponseProcessor) {
		return epochsRemoteSyncResponseProcessor.epochsLedgerUpdateEventProcessor();
	}


	@Provides
	private EventProcessor<LocalSyncRequest> localSyncRequestEventProcessor(EpochsLocalSyncServiceProcessor epochsLocalSyncServiceProcessor) {
		return epochsLocalSyncServiceProcessor.localSyncRequestEventProcessor();
	}

	@Provides
	private RemoteEventProcessor<DtoCommandsAndProof> syncResponseProcessor(EpochsRemoteSyncResponseProcessor processor) {
		return processor.syncResponseProcessor();
	}

	@Provides
	SyncedEpochSender syncedEpochSender(Ledger ledger) {
		return header -> {
			VerifiedCommandsAndProof commandsAndProof = new VerifiedCommandsAndProof(
				ImmutableList.of(),
				header
			);

			ledger.commit(commandsAndProof);
		};
	}

	@Provides
	private Function<BFTConfiguration, RemoteSyncResponseValidatorSetVerifier> accumulatorVerifierFactory(
		VerifiedValidatorSetSender verifiedValidatorSetSender,
		InvalidValidatorSetSender invalidValidatorSetSender
	) {
		return config ->
			new RemoteSyncResponseValidatorSetVerifier(
				verifiedValidatorSetSender,
				invalidValidatorSetSender,
				config.getValidatorSet()
			);
	}

	@Provides
	private Function<BFTConfiguration, LocalSyncServiceAccumulatorProcessor> localSyncFactory(
		Comparator<AccumulatorState> accumulatorComparator,
		RemoteEventDispatcher<DtoLedgerHeaderAndProof> requestDispatcher,
		SyncTimeoutScheduler syncTimeoutScheduler,
		@SyncPatienceMillis int syncPatienceMillis
	) {
		return config ->
			new LocalSyncServiceAccumulatorProcessor(
				requestDispatcher,
				syncTimeoutScheduler,
				accumulatorComparator,
				config.getGenesisHeader(),
				syncPatienceMillis
			);
	}
}
