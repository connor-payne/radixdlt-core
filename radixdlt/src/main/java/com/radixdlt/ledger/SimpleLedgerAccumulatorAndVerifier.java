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

package com.radixdlt.ledger;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.radixdlt.consensus.Hasher;
import com.radixdlt.crypto.Hash;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Hash chain accumulator and verifier
 */
@ThreadSafe
public class SimpleLedgerAccumulatorAndVerifier implements LedgerAccumulator, LedgerAccumulatorVerifier {
	private final Hasher hasher;

	@Inject
	public SimpleLedgerAccumulatorAndVerifier(Hasher hasher) {
		this.hasher = hasher;
	}

	@Override
	public AccumulatorState accumulate(AccumulatorState parent, Hash hash) {
		byte[] concat = new byte[32 * 2];
		parent.getAccumulatorHash().copyTo(concat, 0);
		hash.copyTo(concat, 32);
		Hash nextAccumulatorHash = hasher.hashBytes(concat);
		return new AccumulatorState(
			parent.getStateVersion() + 1,
			nextAccumulatorHash
		);
	}

	@Override
	public <T extends HasHash> boolean verify(AccumulatorState start, ImmutableList<T> commands, AccumulatorState end) {
		AccumulatorState accumulatorState = start;
		for (T command : commands) {
			accumulatorState = this.accumulate(accumulatorState, command.hash());
		}
		return Objects.equals(accumulatorState, end);
	}

	@Override
	public <T extends HasHash> Optional<ImmutableList<T>> verifyAndGetExtension(
		AccumulatorState current,
		ImmutableList<T> commands,
		AccumulatorState tail
	) {
		if (tail.getStateVersion() < current.getStateVersion()) {
			throw new IllegalArgumentException(String.format("Tail %s is has lower state version than current %s", tail, current));
		}

		final long firstVersion = tail.getStateVersion() - commands.size() + 1;
		if (current.getStateVersion() + 1 < firstVersion) {
			// Missing versions
			return Optional.empty();
		}

		if (commands.isEmpty()) {
			return Objects.equals(current, tail) ? Optional.of(ImmutableList.of()) : Optional.empty();
		}

		final int startIndex = (int) (current.getStateVersion() + 1 - firstVersion);
		final ImmutableList<T> extension = commands.subList(startIndex, commands.size());
		if (!verify(current, extension, tail)) {
			// Does not extend
			return Optional.empty();
		}

		return Optional.of(extension);
	}
}