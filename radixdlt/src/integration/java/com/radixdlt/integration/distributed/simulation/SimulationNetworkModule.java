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

package com.radixdlt.integration.distributed.simulation;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.name.Names;
import com.radixdlt.consensus.ConsensusEventsRx;
import com.radixdlt.consensus.SyncEpochsRPCRx;
import com.radixdlt.consensus.SyncVerticesRPCRx;
import com.radixdlt.consensus.bft.BFTEventReducer.BFTEventSender;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.VertexStore.SyncVerticesRPCSender;
import com.radixdlt.consensus.epoch.EmptySyncVerticesRPCSender;
import com.radixdlt.consensus.epoch.EpochManager.SyncEpochsRPCSender;
import com.radixdlt.integration.distributed.simulation.network.SimulationNetwork;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCountersImpl;
import com.radixdlt.network.TimeSupplier;

public class SimulationNetworkModule extends AbstractModule {
	private final boolean getVerticesRPCEnabled;
	private final BFTNode node;
	private final SimulationNetwork simulationNetwork;

	public SimulationNetworkModule(
		boolean getVerticesRPCEnabled,
		BFTNode node,
		SimulationNetwork simulationNetwork
	) {
		this.getVerticesRPCEnabled = getVerticesRPCEnabled;
		this.node = node;
		this.simulationNetwork = simulationNetwork;
	}

	@Override
	protected void configure() {
		bind(ConsensusEventsRx.class).toInstance(simulationNetwork.getNetworkRx(node));
		bind(SyncEpochsRPCRx.class).toInstance(simulationNetwork.getNetworkRx(node));
		bind(SyncVerticesRPCRx.class).toInstance(simulationNetwork.getNetworkRx(node));
		bind(BFTEventSender.class).toInstance(simulationNetwork.getNetworkSender(node));
		bind(SyncVerticesRPCSender.class).toInstance(
			getVerticesRPCEnabled ? simulationNetwork.getSyncSender(node) : EmptySyncVerticesRPCSender.INSTANCE
		);
		bind(SyncEpochsRPCSender.class).toInstance(simulationNetwork.getSyncSender(node));

		// TODO: Move these out
		bind(SystemCounters.class).to(SystemCountersImpl.class).in(Scopes.SINGLETON);
		bind(TimeSupplier.class).toInstance(System::currentTimeMillis);
		bind(BFTNode.class).annotatedWith(Names.named("self")).toInstance(node);
	}
}