/*
 *  (C) Copyright 2020 Radix DLT Ltd
 *
 *  Radix DLT Ltd licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License.  You may obtain a copy of the
 *  License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 *  either express or implied.  See the License for the specific
 *  language governing permissions and limitations under the License.
 */

package com.radixdlt.consensus;

import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.Hash;

import com.radixdlt.middleware2.CommittedAtom;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Manages the BFT Vertex chain.
 *
 * In general this class is NOT thread-safe except for getVertices() and getHighestQC().
 * TODO: make thread-safe
 */
public final class VertexStore {
	private static final Logger log = LogManager.getLogger();

	public interface SyncSender {
		void synced(Hash vertexId);
	}

	private final BehaviorSubject<Vertex> lastCommittedVertex = BehaviorSubject.create();
	private final SyncSender syncSender;
	private final VertexSupplier vertexSupplier;
	private final SyncedStateComputer<CommittedAtom> syncedStateComputer;
	private final SystemCounters counters;
	private final Object lock = new Object();


	// Should never be null
	private volatile Hash rootId;
	private volatile QuorumCertificate highestQC;
	private volatile QuorumCertificate highestCommittedQC;
	private final Map<Hash, Vertex> vertices = new ConcurrentHashMap<>();
	private final Map<Hash, Disposable> syncing = new ConcurrentHashMap<>();

	// TODO: Cleanup this interface
	public VertexStore(
		Vertex genesisVertex,
		QuorumCertificate rootQC,
		SyncedStateComputer<CommittedAtom> syncedStateComputer,
		VertexSupplier vertexSupplier,
		SyncSender syncSender,
		SystemCounters counters
	) {
		this.syncedStateComputer = syncedStateComputer;
		this.vertexSupplier = vertexSupplier;
		this.syncSender = syncSender;
		this.counters = Objects.requireNonNull(counters);
		this.highestQC = Objects.requireNonNull(rootQC);
		this.highestCommittedQC = rootQC;

		if (genesisVertex.getAtom() != null) {
			CommittedAtom committedGenesis = genesisVertex.getAtom().committed(rootQC.getProposed());
			syncedStateComputer.execute(committedGenesis);
		}

		this.rootId = genesisVertex.getId();
		this.vertices.put(rootId, genesisVertex);
		this.lastCommittedVertex.onNext(genesisVertex);
	}

	private Observable<List<Vertex>> fetchVertices(QuorumCertificate qc, ECPublicKey author) {
		if (!vertices.containsKey(qc.getProposed().getId())) {
			log.info("Sending GET_VERTICES to {}: {}", author, qc);
			return vertexSupplier.getVertices(qc.getProposed().getId(), author, 1)
				.doOnSuccess(v -> log.info("Received GET_VERTICES: {}", v))
				.toObservable()
				.flatMap(v -> v.isEmpty()
					? Observable.just(v)
					: fetchVertices(v.get(0).getQC(), author).concatWith(Observable.just(v))
				);
		} else {
			return Observable.empty();
		}
	}

	private void doSync(QuorumCertificate qc, ECPublicKey author) {
		final Hash vertexId = qc.getProposed().getId();
		if (syncing.containsKey(vertexId)) {
			return;
		}

		Disposable d = this.fetchVertices(qc, author)
			.observeOn(Schedulers.io())
			.toList()
			.subscribe(
				vertices -> {
					if (!syncing.containsKey(vertexId)) {
						return;
					}

					// Failed to retrieve all ancestors
					if (vertices.stream().anyMatch(List::isEmpty)) {
						log.info("GET_VERTICES failed: {}", qc);
						return;
					}

					// TODO: Better Locking mechanism
					synchronized (lock) {
						for (List<Vertex> vertexSingle : vertices) {
							Vertex vertex = vertexSingle.get(0);
							if (!addQC(vertex.getQC())) {
								log.info("GET_VERTICES failed: {}", qc);
								return;
							}
							insertVertex(vertex);
						}
						addQC(qc);
					}
				},
				e -> log.info("GET_VERTICES failed: {} {}", qc, e.getMessage())
			);

		syncing.put(vertexId, d);
	}

	public void processLocalSync(Hash vertexId) {
		syncing.remove(vertexId);
	}

	public boolean syncToQC(QuorumCertificate qc, QuorumCertificate committedQC, @Nullable ECPublicKey author) {
		if (addQC(qc)) {
			return true;
		}

		if (author != null) {
			this.doSync(qc, author);
		}


		/*
		Optional<VertexMetadata> committed = committedQC.getCommitted();
		if (!committed.isPresent()) {
			return false;
		}

		VertexMetadata committedMetadata = committed.get();
		if (root.getView().compareTo(committedMetadata.getView()) < 0) {

			long stateVersion = committed.get().getStateVersion();
			final Hash hash;
			try {
				// TODO: Make it easier to retrieve signatures of QC
				hash = Hash.of(DefaultSerialization.getInstance().toDson(committedQC.getVoteData(), Output.HASH));
			} catch (SerializationException e) {
				throw new IllegalStateException("Failed to serialize");
			}				List<ECPublicKey> signers = committedQC.getSignatures().signedMessage(hash);

			Observable.combineLatest(
				vertexSupplier.getVertices(committedQC.getProposed().getId(), author, 3).toObservable(),
				syncedStateComputer.syncTo(stateVersion, signers).toSingleDefault(0).toObservable(),
				(v, l) -> v
			).subscribe(vertices -> {
				synchronized (lock) {
					if (root.getView().compareTo(committedMetadata.getView()) < 0) {
						uncommitted.clear();
						for (Vertex v : vertices) {
							if (!v.getId().equals(committedMetadata.getId())) {
								uncommitted.put(v.getId(), v);
							}
						}
						this.root = committedMetadata;
						this.highestCommittedQC = committedQC;
						this.highestQC = committedQC;
					}
				}
			});
		}
		*/

		return false;
	}

	private boolean addQC(QuorumCertificate qc) {
		synchronized (lock) {
			if (!vertices.containsKey(qc.getProposed().getId())) {
				return false;
			}

			if (highestQC.getView().compareTo(qc.getView()) < 0) {
				highestQC = qc;
			}

			qc.getCommitted().ifPresent(vertexMetadata -> {
				if (!highestCommittedQC.getCommitted().isPresent()
					|| highestCommittedQC.getCommitted().get().getStateVersion() < vertexMetadata.getStateVersion()) {
					this.highestCommittedQC = qc;
				}
			});

			return true;
		}
	}

	public void insertVertex(Vertex vertex) throws VertexInsertionException {
		synchronized (lock) {
			if (!vertices.containsKey(vertex.getParentId())) {
				throw new MissingParentException(vertex.getParentId());
			}

			// TODO: Don't check for state computer errors for now so that we don't
			// TODO: have to deal with failing leader proposals
			// TODO: Reinstate this when ProposalGenerator + Mempool can guarantee correct proposals
			// TODO: (also see commitVertex->storeAtom)

			vertices.put(vertex.getId(), vertex);
			updateVertexStoreSize();

			if (syncing.containsKey(vertex.getId())) {
				syncing.get(vertex.getId()).dispose();
				syncSender.synced(vertex.getId());
			}
		}
	}

	// TODO: add signature proof
	public Vertex commitVertex(VertexMetadata commitMetadata) {
		synchronized (lock) {
			final Hash vertexId = commitMetadata.getId();
			final Vertex tipVertex = vertices.get(vertexId);
			if (tipVertex == null) {
				throw new IllegalStateException("Committing a vertex which was never inserted: " + vertexId);
			}
			final LinkedList<Vertex> path = new LinkedList<>();
			Vertex vertex = tipVertex;
			while (vertex != null && !rootId.equals(vertex.getId())) {
				path.addFirst(vertex);
				vertex = vertices.remove(vertex.getParentId());
			}

			for (Vertex committed : path) {
				if (committed.getAtom() != null) {
					CommittedAtom committedAtom = committed.getAtom().committed(commitMetadata);
					this.counters.increment(CounterType.CONSENSUS_PROCESSED);
					syncedStateComputer.execute(committedAtom);
				}

				lastCommittedVertex.onNext(committed);
			}

			rootId = commitMetadata.getId();

			updateVertexStoreSize();
			return tipVertex;
		}
	}

	public Observable<Vertex> lastCommittedVertex() {
		return lastCommittedVertex;
	}

	public List<Vertex> getPathFromRoot(Hash vertexId) {
		final List<Vertex> path = new ArrayList<>();

		Vertex vertex = vertices.get(vertexId);
		while (vertex != null && !vertex.getId().equals(rootId)) {
			path.add(vertex);
			vertex = vertices.get(vertex.getParentId());
		}

		return path;
	}

	/**
	 * Retrieves the highest committed qc in the store
	 * @return the highest committed qc
	 */
	public QuorumCertificate getHighestCommittedQC() {
		return this.highestCommittedQC;
	}

	/**
	 * Retrieves the highest qc in the store
	 * Thread-safe.
	 *
	 * @return the highest quorum certificate
	 */
	public QuorumCertificate getHighestQC() {
		return this.highestQC;
	}

	/**
	 * Retrieves the vertex with the given vertexId if it exists in the store.
	 * Thread-safe.
	 *
	 * @param vertexId the id of the vertex
	 * @param count the number of verticies to retrieve
	 * @return the vertex or null, if it is not stored
	 */
	public List<Vertex> getVertices(Hash vertexId, int count) {
		Hash nextId = vertexId;
		List<Vertex> response = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			Vertex vertex = this.vertices.get(nextId);
			if (vertex == null) {
				return Collections.emptyList();
			}

			response.add(vertex);
			nextId = vertex.getParentId();
		}

		return response;
	}

	public void clearSyncs() {
		syncing.forEach((h, d) -> d.dispose());
		syncing.clear();
	}

	public int getSize() {
		return vertices.size();
	}

	private void updateVertexStoreSize() {
		this.counters.set(CounterType.CONSENSUS_VERTEXSTORE_SIZE, this.vertices.size());
	}
}
