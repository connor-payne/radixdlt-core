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

package com.radixdlt.consensus.functional.synchronous;

import static org.assertj.core.api.Assertions.assertThat;

import com.radixdlt.consensus.NewView;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.functional.BFTFunctionalTest;
import com.radixdlt.counters.SystemCounters.CounterType;
import org.junit.Test;

public class OneSlowNodeTest {

	/**
	 * Because syncing more than 1 vertex is not yet supported, this
	 * tests for sync exception when three nodes go way ahead of one slow node.
	 */
	@Test
	public void when_three_fast_nodes_and_one_slow_node__then_missing_parent_will_cause_sync_exception() {
		final BFTFunctionalTest test = new BFTFunctionalTest(4);
		for (int curLeader = 1; curLeader <= 3; curLeader++) {
			test.processNextMsg(curLeader, 1, NewView.class);
			test.processNextMsg(curLeader, 2, NewView.class);
			test.processNextMsg(curLeader, 3, NewView.class);

			test.processNextMsg(1, curLeader, Proposal.class);
			test.processNextMsg(2, curLeader, Proposal.class);
			test.processNextMsg(3, curLeader, Proposal.class);

			test.processNextMsg(curLeader, 1, Vote.class);
			test.processNextMsg(curLeader, 2, Vote.class);
			test.processNextMsg(curLeader, 3, Vote.class);
		}

		test.processNextMsg(0, 3, Proposal.class);
		assertThat(test.getSystemCounters(0).get(CounterType.CONSENSUS_SYNC_EXCEPTION)).isEqualTo(1);
	}
}