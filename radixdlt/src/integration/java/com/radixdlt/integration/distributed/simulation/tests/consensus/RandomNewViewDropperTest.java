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

package com.radixdlt.integration.distributed.simulation.tests.consensus;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.google.inject.util.Modules;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.integration.distributed.simulation.FixedLatencyModule;
import com.radixdlt.integration.distributed.simulation.SimulationTest;
import com.radixdlt.integration.distributed.simulation.SimulationTest.Builder;
import com.radixdlt.integration.distributed.simulation.SimulationTest.TestResults;
import com.radixdlt.integration.distributed.simulation.network.RandomNewViewDropper;
import com.radixdlt.integration.distributed.simulation.network.SimulationNetwork.MessageInTransit;
import java.util.LongSummaryStatistics;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.Test;

/**
 * Dropping random new-views should cause consensus to fork quite a bit.
 * This is to test that safety should always be preserved even in multiple forking situations.
 */
public class RandomNewViewDropperTest {
	private final Builder bftTestBuilder = SimulationTest.builder()
		.numNodes(4)
		.networkModule(Modules.combine(
			new FixedLatencyModule(),
			new AbstractModule() {
				@ProvidesIntoSet
				Predicate<MessageInTransit> dropper() {
					return new RandomNewViewDropper(new Random(), 0.4);
				}
			}
		))
		.checkConsensusSafety("safety")
		.checkConsensusLiveness("liveness", 20, TimeUnit.SECONDS);

	/**
	 * Tests a configuration of 4 nodes with a dropping proposal adversary
	 * Test should fail with GetVertices RPC disabled
	 */
	@Test
	public void sanity_test() {
		SimulationTest test = bftTestBuilder.build();
		TestResults results = test.run();

		LongSummaryStatistics statistics = results.getNetwork().getSystemCounters().values().stream()
			.map(s -> s.get(CounterType.BFT_VERTEX_STORE_FORKS))
			.mapToLong(l -> l)
			.summaryStatistics();
		System.out.println(statistics);

		assertThat(results.getCheckResults()).allSatisfy((name, error) -> AssertionsForClassTypes.assertThat(error).isNotPresent());
	}
}
