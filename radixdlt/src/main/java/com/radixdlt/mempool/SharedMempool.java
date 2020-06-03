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

package com.radixdlt.mempool;

import com.radixdlt.middleware2.ClientAtom;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import javax.inject.Inject;

import com.radixdlt.identifiers.AID;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;

/**
 * Shared mempool.
 * <p>
 * Shares contents with network.
 * Threadsafe.
 */
public class SharedMempool implements Mempool {
	private final LocalMempool localMempool;
	private final MempoolNetworkTx networkSender;
	private final SystemCounters counters;

	@Inject
	SharedMempool(SystemCounters counters, LocalMempool localMempool, MempoolNetworkTx networkSender) {
		this.localMempool = Objects.requireNonNull(localMempool);
		this.networkSender = Objects.requireNonNull(networkSender);
		this.counters = Objects.requireNonNull(counters);
	}

	@Override
	public void addAtom(ClientAtom atom) throws MempoolFullException, MempoolDuplicateException {
		this.localMempool.addAtom(atom);
		updateCounts();
		this.networkSender.sendMempoolSubmission(atom);
	}

	@Override
	public void removeCommittedAtom(AID aid) {
		this.localMempool.removeCommittedAtom(aid);
		updateCounts();
	}

	@Override
	public void removeRejectedAtom(AID aid) {
		this.localMempool.removeRejectedAtom(aid);
		updateCounts();
	}

	@Override
	public List<ClientAtom> getAtoms(int count, Set<AID> seen) {
		return this.localMempool.getAtoms(count, seen);
	}

	@Override
	public int atomCount() {
		return this.localMempool.atomCount();
	}

	private void updateCounts() {
		this.counters.set(CounterType.MEMPOOL_COUNT, atomCount());
		this.counters.set(CounterType.MEMPOOL_MAXCOUNT, this.localMempool.maxCount());
	}

	@Override
	public String toString() {
		return String.format("%s[%x:%s/%s]",
			getClass().getSimpleName(), System.identityHashCode(this), atomCount(), this.localMempool.maxCount());
	}
}
