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

package com.radixdlt.consensus.deterministic.tests.bft.synchronous;

import com.radixdlt.consensus.deterministic.DeterministicTest;
import java.util.Random;
import org.junit.Test;

public class RandomChannelOrderResponsiveTest {
	private static final int NUM_STEPS = 30000;

	@Test
	public void when_run_4_correct_nodes_with_channel_order_random_and_timeouts_disabled__then_bft_should_be_responsive() {
		final Random random = new Random(12345);
		final DeterministicTest test = DeterministicTest.createSingleEpochAlwaysSyncedTest(4);

		test.start();
		for (int step = 0; step < NUM_STEPS; step++) {
			test.processNextMsg(random);
		}
	}

	@Test
	public void when_run_100_correct_nodes_with_channel_order_random_and_timeouts_disabled__then_bft_should_be_responsive() {
		final Random random = new Random(12345);
		final DeterministicTest test = DeterministicTest.createSingleEpochAlwaysSyncedTest(100);

		test.start();
		for (int step = 0; step < NUM_STEPS; step++) {
			test.processNextMsg(random);
		}
	}
}