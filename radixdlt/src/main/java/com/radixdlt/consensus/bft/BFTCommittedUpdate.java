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

package com.radixdlt.consensus.bft;

import com.google.common.collect.ImmutableList;
import com.radixdlt.consensus.HighQC;
import java.util.Objects;

/**
 * Vertex Store update of committed vertices
 */
public final class BFTCommittedUpdate {
	private final ImmutableList<PreparedVertex> committed;
	private final HighQC highQC;
	private final int vertexStoreSize;

	BFTCommittedUpdate(ImmutableList<PreparedVertex> committed, HighQC highQC, int vertexStoreSize) {
		this.committed = Objects.requireNonNull(committed);
		this.highQC = Objects.requireNonNull(highQC);
		this.vertexStoreSize = vertexStoreSize;
	}

	public int getVertexStoreSize() {
		return vertexStoreSize;
	}

	public ImmutableList<PreparedVertex> getCommitted() {
		return committed;
	}

	public HighQC getProof() {
		return highQC;
	}
}
