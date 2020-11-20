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

package com.radixdlt.integration.recovery;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Scopes;
import com.google.inject.name.Names;
import com.radixdlt.ConsensusModule;
import com.radixdlt.CryptoModule;
import com.radixdlt.EpochsConsensusModule;
import com.radixdlt.EpochsLedgerUpdateModule;
import com.radixdlt.EpochsSyncModule;
import com.radixdlt.LedgerCommandGeneratorModule;
import com.radixdlt.LedgerModule;
import com.radixdlt.NoFeeModule;
import com.radixdlt.PersistenceModule;
import com.radixdlt.RadixEngineModule;
import com.radixdlt.RadixEngineStoreModule;
import com.radixdlt.SyncServiceModule;
import com.radixdlt.consensus.HashSigner;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.PacemakerMaxExponent;
import com.radixdlt.consensus.bft.PacemakerRate;
import com.radixdlt.consensus.bft.PacemakerTimeout;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.epoch.EpochView;
import com.radixdlt.consensus.sync.BFTSyncPatienceMillis;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCountersImpl;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.environment.deterministic.DeterministicEpochInfo;
import com.radixdlt.integration.distributed.MockedMempoolModule;
import com.radixdlt.environment.deterministic.DeterministicEpochsConsensusProcessor;
import com.radixdlt.environment.deterministic.DeterministicEnvironmentModule;
import com.radixdlt.environment.deterministic.ControlledSenderFactory;
import com.radixdlt.environment.deterministic.network.ControlledMessage;
import com.radixdlt.environment.deterministic.network.DeterministicNetwork;
import com.radixdlt.environment.deterministic.network.MessageMutator;
import com.radixdlt.environment.deterministic.network.MessageSelector;
import com.radixdlt.ledger.VerifiedCommandsAndProof;
import com.radixdlt.network.TimeSupplier;
import com.radixdlt.properties.RuntimeProperties;
import com.radixdlt.statecomputer.EpochCeilingView;
import com.radixdlt.statecomputer.MinValidators;
import com.radixdlt.statecomputer.RadixEngineStateComputer.CommittedAtomSender;
import com.radixdlt.sync.LocalSyncServiceAccumulatorProcessor.SyncTimeoutScheduler;
import com.radixdlt.sync.SyncPatienceMillis;
import com.radixdlt.utils.UInt256;
import io.reactivex.rxjava3.schedulers.Timed;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.cli.ParseException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * A deterministic node test which uses a real database.
 * Consensus is executed for a few views/epochs until a new injector instance
 * is created, somewhat similar to a process restart. Consensus should then
 * be able to run correctly again from the new injector.
 * This is repeated a certain number of times.
 */
@RunWith(Parameterized.class)
public class AllNodesDieAndRecoverTest {

	@Parameters
	public static Collection<Object[]> numNodes() {
		return List.of(new Object[][] {
			{1}
		});
	}

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	private final DeterministicNetwork network;
	private List<Supplier<Injector>> nodeCreators;
	private List<Injector> nodes = new ArrayList<>();

	public AllNodesDieAndRecoverTest(int numNodes) {
		final List<ECKeyPair> nodeKeys = Stream.generate(ECKeyPair::generateNew).limit(numNodes).collect(Collectors.toList());

		this.network = new DeterministicNetwork(
			nodeKeys.stream().map(k -> BFTNode.create(k.getPublicKey())).collect(Collectors.toList()),
			MessageSelector.firstSelector(),
			MessageMutator.nothing()
		);

		this.nodeCreators = nodeKeys.stream()
			.<Supplier<Injector>>map(k -> () -> createRunner(k))
			.collect(Collectors.toList());
	}

	@Before
	public void setup() {
		for (Supplier<Injector> nodeCreator : nodeCreators) {
			this.nodes.add(nodeCreator.get());
		}
		this.nodes.forEach(i -> i.getInstance(DeterministicEpochsConsensusProcessor.class).start());
	}

	private Injector createRunner(ECKeyPair ecKeyPair) {
		final BFTNode self = BFTNode.create(ecKeyPair.getPublicKey());

		return Guice.createInjector(
			new AbstractModule() {
				@Override
				protected void configure() {
					bind(HashSigner.class).toInstance(ecKeyPair::sign);
					bind(BFTNode.class).annotatedWith(Self.class).toInstance(self);
					bindConstant().annotatedWith(Names.named("magic")).to(0);
					bind(ControlledSenderFactory.class).toInstance(network::createSender);

					bind(Integer.class).annotatedWith(SyncPatienceMillis.class).toInstance(200);
					bind(Integer.class).annotatedWith(BFTSyncPatienceMillis.class).toInstance(200);
					bind(Integer.class).annotatedWith(MinValidators.class).toInstance(1);
					bind(Long.class).annotatedWith(PacemakerTimeout.class).toInstance(1000L);
					bind(Double.class).annotatedWith(PacemakerRate.class).toInstance(2.0);
					bind(Integer.class).annotatedWith(PacemakerMaxExponent.class).toInstance(6);
					bind(View.class).annotatedWith(EpochCeilingView.class).toInstance(View.of(100L));

					// System
					bind(SystemCounters.class).to(SystemCountersImpl.class).in(Scopes.SINGLETON);
					bind(TimeSupplier.class).toInstance(System::currentTimeMillis);

					// TODO: Move these into DeterministicSender
					bind(CommittedAtomSender.class).toInstance(atom -> { });
					bind(SyncTimeoutScheduler.class).toInstance((syncInProgress, milliseconds) -> { });

					// Checkpoint
					VerifiedLedgerHeaderAndProof genesisLedgerHeader = VerifiedLedgerHeaderAndProof.genesis(
						HashUtils.zero256(),
						BFTValidatorSet.from(Stream.of(BFTValidator.from(self, UInt256.ONE)))
					);
					bind(VerifiedCommandsAndProof.class).toInstance(new VerifiedCommandsAndProof(
						ImmutableList.of(),
						genesisLedgerHeader
					));

					final RuntimeProperties runtimeProperties;
					// TODO: this constructor/class/inheritance/dependency is horribly broken
					try {
						runtimeProperties = new RuntimeProperties(new JSONObject(), new String[0]);
						runtimeProperties.set("db.location", folder.getRoot().getAbsolutePath() + "/RADIXDB_RECOVERY_TEST_" + self);
					} catch (ParseException e) {
						throw new IllegalStateException();
					}
					bind(RuntimeProperties.class).toInstance(runtimeProperties);
				}
			},

			new DeterministicEnvironmentModule(),

			// Consensus
			new CryptoModule(),
			new ConsensusModule(),

			// Ledger
			new LedgerModule(),
			new LedgerCommandGeneratorModule(),
			new MockedMempoolModule(),

			// Sync
			new SyncServiceModule(),

			// Epochs - Consensus
			new EpochsConsensusModule(),
			// Epochs - Ledger
			new EpochsLedgerUpdateModule(),
			// Epochs - Sync
			new EpochsSyncModule(),

			// State Computer
			new RadixEngineModule(),
			new RadixEngineStoreModule(),

			// Fees
			new NoFeeModule(),

			new PersistenceModule()
		);
	}

	private void restartNode(int index) {
		this.network.dropMessages(m -> m.channelId().receiverIndex() == index && m.channelId().senderIndex() == index);
		Injector injector = nodeCreators.get(index).get();
		this.nodes.set(index, injector);
		injector.getInstance(DeterministicEpochsConsensusProcessor.class).start();
	}

	private void processForCount(int messageCount) {
		for (int i = 0; i < messageCount; i++) {
			Timed<ControlledMessage> msg = this.network.nextMessage();
			Injector injector = this.nodes.get(msg.value().channelId().receiverIndex());
			injector.getInstance(DeterministicEpochsConsensusProcessor.class).handleMessage(msg.value().origin(), msg.value().message());
		}
	}

	@Test
	@Ignore("Liveness broken if all nodes restart: To be fixed in RPNV1-758")
	public void all_nodes_restart_liveness_should_not_be_broken() {
		EpochView epochView = this.nodes.get(0).getInstance(DeterministicEpochInfo.class).getCurrentEpochView();

		for (int restart = 0; restart < 100; restart++) {
			processForCount(2000);

			EpochView nextEpochView = this.nodes.stream().map(i -> i.getInstance(DeterministicEpochInfo.class).getCurrentEpochView())
				.max(Comparator.naturalOrder()).orElse(new EpochView(0, View.genesis()));
			assertThat(nextEpochView).isGreaterThan(epochView);
			epochView = nextEpochView;

			for (int nodeIndex = 0; nodeIndex < nodes.size(); nodeIndex++) {
				restartNode(nodeIndex);
			}
		}
	}
}
