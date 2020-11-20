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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.HighQC;
import com.radixdlt.consensus.Ledger;
import com.radixdlt.consensus.BFTHeader;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.google.common.hash.HashCode;

import com.google.common.collect.ImmutableList;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.utils.Pair;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * Manages the BFT Vertex chain.
 * TODO: Move this logic into ledger package.
 */
@NotThreadSafe
public final class VertexStore {

	// TODO: combine all of the following senders as an update sender
	public interface VertexStoreEventSender {
		void highQC(QuorumCertificate qc);
	}

	private final VertexStoreEventSender vertexStoreEventSender;
	private final EventDispatcher<BFTUpdate> bftUpdateDispatcher;
	private final EventDispatcher<BFTCommittedUpdate> bftCommittedDispatcher;
	private final Ledger ledger;
	private final SystemCounters counters;

	private final Map<HashCode, PreparedVertex> vertices = new HashMap<>();
	private final Map<HashCode, Set<HashCode>> vertexChildren = new HashMap<>();

	// These should never be null
	private VerifiedVertex rootVertex;
	private QuorumCertificate highestQC;
	private QuorumCertificate highestCommittedQC;

	private VertexStore(
		Ledger ledger,
		VerifiedVertex rootVertex,
		QuorumCertificate rootQC,
		EventDispatcher<BFTUpdate> bftUpdateDispatcher,
		EventDispatcher<BFTCommittedUpdate> bftCommittedDispatcher,
		VertexStoreEventSender vertexStoreEventSender,
		SystemCounters counters
	) {
		this.ledger = Objects.requireNonNull(ledger);
		this.vertexStoreEventSender = Objects.requireNonNull(vertexStoreEventSender);
		this.bftUpdateDispatcher = Objects.requireNonNull(bftUpdateDispatcher);
		this.bftCommittedDispatcher = Objects.requireNonNull(bftCommittedDispatcher);
		this.counters = Objects.requireNonNull(counters);
		this.rootVertex = Objects.requireNonNull(rootVertex);
		this.highestQC = Objects.requireNonNull(rootQC);
		this.highestCommittedQC = rootQC;
	}

	public static VertexStore create(
		VerifiedVertex rootVertex,
		QuorumCertificate rootQC,
		Ledger ledger,
		EventDispatcher<BFTUpdate> bftUpdateDispatcher,
		EventDispatcher<BFTCommittedUpdate> bftCommittedDispatcher,
		VertexStoreEventSender vertexStoreEventSender,
		SystemCounters counters
	) {
		if (!rootQC.getProposed().getVertexId().equals(rootVertex.getId())) {
			throw new IllegalStateException(String.format("rootQC=%s does not match rootVertex=%s", rootQC, rootVertex));
		}

		final Optional<BFTHeader> maybeHeader = rootQC.getCommittedAndLedgerStateProof().map(Pair::getFirst);
		final BFTHeader committedHeader = maybeHeader.orElseThrow(
			() -> new IllegalStateException(String.format("rootCommit=%s does not have commit", rootQC))
		);
		if (!committedHeader.getVertexId().equals(rootVertex.getId())) {
			throw new IllegalStateException(String.format("rootCommitQC=%s does not match rootVertex=%s", rootQC, rootVertex));
		}

		return new VertexStore(
			ledger,
			rootVertex,
			rootQC,
			bftUpdateDispatcher,
			bftCommittedDispatcher,
			vertexStoreEventSender,
			counters
		);
	}

	public VerifiedVertex getRoot() {
		return rootVertex;
	}

	public void rebuild(VerifiedVertex rootVertex, QuorumCertificate rootQC, QuorumCertificate rootCommitQC, List<VerifiedVertex> vertices) {
		if (!rootQC.getProposed().getVertexId().equals(rootVertex.getId())) {
			throw new IllegalStateException(String.format("rootQC=%s does not match rootVertex=%s", rootQC, rootVertex));
		}

		final Optional<BFTHeader> header = rootCommitQC.getCommittedAndLedgerStateProof().map(Pair::getFirst);
		if (!header.isPresent()) {
			if (!rootQC.getView().isGenesis() || !rootQC.equals(rootCommitQC)) {
				throw new IllegalStateException(String.format("rootCommit=%s does not have commit", rootCommitQC));
			}
		} else if (!header.get().getVertexId().equals(rootVertex.getId())) {
			throw new IllegalStateException(String.format("rootCommitQC=%s does not match rootVertex=%s", rootCommitQC, rootVertex));
		}

		this.vertices.clear();
		this.vertexChildren.clear();
		this.rootVertex = rootVertex;
		this.highestQC = rootQC;
		this.vertexStoreEventSender.highQC(rootQC);
		this.highestCommittedQC = rootCommitQC;

		// TODO: combine all these bft updates into one
		BFTUpdate bftUpdate = new BFTUpdate(rootVertex, 0);
		bftUpdateDispatcher.dispatch(bftUpdate);

		for (VerifiedVertex vertex : vertices) {
			if (!addQC(vertex.getQC())) {
				throw new IllegalStateException(String.format("Missing qc=%s vertices=%s", vertex.getQC(), vertices));
			}

			// An insertion may have failed due to the ledger being ahead of vertex store
			// in this case, just stop inserting vertices for now
			// TODO: fix once more persistent prepare branches are implemented
			Optional<BFTHeader> maybeInserted = insertVertex(vertex);
			if (maybeInserted.isEmpty()) {
				break;
			}
		}
	}

	public boolean containsVertex(HashCode vertexId) {
		return vertices.containsKey(vertexId) || rootVertex.getId().equals(vertexId);
	}

	public boolean addQC(QuorumCertificate qc) {
		if (!this.containsVertex(qc.getProposed().getVertexId())) {
			return false;
		}

		// TODO: check if already added

		if (highestQC.getView().compareTo(qc.getView()) < 0) {
			highestQC = qc;
			vertexStoreEventSender.highQC(qc);
		}

		qc.getCommittedAndLedgerStateProof().ifPresent(headerAndProof -> {
			BFTHeader highest = this.highestCommittedQC.getCommittedAndLedgerStateProof()
				.map(Pair::getFirst)
				.orElseThrow(() ->
					new IllegalStateException(String.format("Highest Committed does not have a commit: %s", this.highestCommittedQC))
				);

			BFTHeader header = headerAndProof.getFirst();
			if (highest.getView().compareTo(header.getView()) < 0) {
				this.highestCommittedQC = qc;
			}

			this.commit(header, highQC());
		});

		return true;
	}

	/**
	 * Inserts a vertex and then attempts to create the next header.
	 * If the ledger is ahead of the vertex store then returns an empty optional
	 * otherwise an empty optional.
	 *
	 * @param vertex vertex to insert
	 * @return a bft header if creation of next header is successful.
	 */
	public Optional<BFTHeader> insertVertex(VerifiedVertex vertex) {
		if (!this.containsVertex(vertex.getParentId())) {
			throw new MissingParentException(vertex.getParentId());
		}

		LinkedList<PreparedVertex> previous = getPathFromRoot(vertex.getParentId());
		Optional<PreparedVertex> preparedVertexMaybe = ledger.prepare(previous, vertex);
		preparedVertexMaybe.ifPresent(preparedVertex -> {
			// TODO: Don't check for state computer errors for now so that we don't
			// TODO: have to deal with failing leader proposals
			// TODO: Reinstate this when ProposalGenerator + Mempool can guarantee correct proposals
			// TODO: (also see commitVertex->storeAtom)
			if (!vertices.containsKey(preparedVertex.getId())) {
				vertices.put(preparedVertex.getId(), preparedVertex);
				Set<HashCode> siblings = vertexChildren.computeIfAbsent(preparedVertex.getParentId(), i -> new HashSet<>());
				siblings.add(preparedVertex.getId());
				if (siblings.size() > 1) {
					this.counters.increment(CounterType.BFT_VERTEX_STORE_FORKS);
				}
				if (!vertex.hasDirectParent()) {
					this.counters.increment(CounterType.BFT_INDIRECT_PARENT);
				}

				updateVertexStoreSize();

				final BFTUpdate update = new BFTUpdate(vertex, this.vertices.size());
				bftUpdateDispatcher.dispatch(update);
			}
		});

		return preparedVertexMaybe
			.map(executedVertex -> new BFTHeader(vertex.getView(), vertex.getId(), executedVertex.getLedgerHeader()));
	}

	private void removeVertexAndPruneInternal(HashCode vertexId, HashCode skip, Builder<HashCode> prunedVerticesBuilder) {
		vertices.remove(vertexId);

		if (this.rootVertex.getId().equals(vertexId)) {
			return;
		}

		if (skip != null) {
			prunedVerticesBuilder.add(vertexId);
		}

		Set<HashCode> children = vertexChildren.remove(vertexId);
		if (children != null) {
			for (HashCode child : children) {
				if (!child.equals(skip)) {
					removeVertexAndPruneInternal(child, null, prunedVerticesBuilder);
				}
			}
		}
	}

	/**
	 * Commit a vertex. Executes the atom and prunes the tree.
	 * @param header the header to be committed
	 * @param highQC the proof of commit
	 */
	private void commit(BFTHeader header, HighQC highQC) {
		if (header.getView().compareTo(this.rootVertex.getView()) <= 0) {
			return;
		}

		final HashCode vertexId = header.getVertexId();
		final VerifiedVertex tipVertex = vertices.get(vertexId).getVertex();
		if (tipVertex == null) {
			throw new IllegalStateException("Committing vertex not in store: " + header);
		}

		this.rootVertex = tipVertex;
		Builder<HashCode> prunedSetBuilder = ImmutableSet.builder();
		final ImmutableList<PreparedVertex> path = ImmutableList.copyOf(getPathFromRoot(tipVertex.getId()));
		HashCode prev = null;
		for (int i = path.size() - 1; i >= 0; i--) {
			this.removeVertexAndPruneInternal(path.get(i).getId(), prev, prunedSetBuilder);
			prev = path.get(i).getId();
		}
		ImmutableSet<HashCode> prunedSet = prunedSetBuilder.build();

		this.counters.add(CounterType.BFT_PROCESSED, path.size());
		final BFTCommittedUpdate bftCommittedUpdate = new BFTCommittedUpdate(path, highQC, this.vertices.size());
		this.bftCommittedDispatcher.dispatch(bftCommittedUpdate);

		this.ledger.commit(path, highQC, prunedSet);

		updateVertexStoreSize();
	}

	public LinkedList<PreparedVertex> getPathFromRoot(HashCode vertexId) {
		final LinkedList<PreparedVertex> path = new LinkedList<>();

		PreparedVertex vertex = vertices.get(vertexId);
		while (vertex != null) {
			path.addFirst(vertex);
			vertex = vertices.get(vertex.getParentId());
		}

		return path;
	}

	/**
	 * Retrieves the highest QC and highest committed QC in the store.
	 *
	 * @return the highest QCs
	 */
	public HighQC highQC() {
		return HighQC.from(this.highestQC, this.highestCommittedQC);
	}

	/**
	 * Retrieves list of vertices starting with the given vertexId and
	 * then proceeding to its ancestors.
	 *
	 * if the store does not contain some vertex then will return an empty
	 * list.
	 *
	 * @param vertexId the id of the vertex
	 * @param count the number of vertices to retrieve
	 * @return the list of vertices if all found, otherwise an empty list
	 */
	public Optional<ImmutableList<VerifiedVertex>> getVertices(HashCode vertexId, int count) {
		HashCode nextId = vertexId;
		ImmutableList.Builder<VerifiedVertex> builder = ImmutableList.builderWithExpectedSize(count);
		for (int i = 0; i < count; i++) {
			final VerifiedVertex verifiedVertex;
			if (nextId.equals(rootVertex.getId())) {
				verifiedVertex = rootVertex;
			} else if (this.vertices.containsKey(nextId)) {
				final PreparedVertex preparedVertex = this.vertices.get(nextId);
				verifiedVertex = preparedVertex.getVertex();
			} else {
				return Optional.empty();
			}

			builder.add(verifiedVertex);
			nextId = verifiedVertex.getParentId();
		}

		return Optional.of(builder.build());
	}

	public int getSize() {
		return vertices.size();
	}

	// TODO: Move counters into DispatcherModule
	private void updateVertexStoreSize() {
		this.counters.set(CounterType.BFT_VERTEX_STORE_SIZE, this.vertices.size());
	}
}
