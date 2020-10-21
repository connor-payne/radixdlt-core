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

package com.radixdlt.integration.distributed.simulation.application;

import com.radixdlt.consensus.bft.View;
import com.radixdlt.integration.distributed.simulation.TestInvariant;
import com.radixdlt.integration.distributed.simulation.network.SimulationNodes.RunningNetwork;
import com.radixdlt.utils.Pair;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;

public class TimestampChecker implements TestInvariant {
	private boolean timestampOkay(long timestamp) {
		long now = System.currentTimeMillis();
		return timestamp <= now && timestamp > now - 15_000;
	}

	@Override
	public Observable<TestInvariantError> check(RunningNetwork network) {
		return network.ledgerUpdates()
			.map(Pair::getSecond)
			.filter(l -> !(l.getTail().getEpoch() == 1 && l.getTail().getView().equals(View.of(1))))
			.flatMapMaybe(update -> timestampOkay(update.getTail().timestamp())
				? Maybe.empty()
				: Maybe.just(new TestInvariantError("bad timestamp: " + update.getTail()))
			);
	}
}