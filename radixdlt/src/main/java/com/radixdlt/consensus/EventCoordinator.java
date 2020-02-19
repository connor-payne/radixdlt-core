package com.radixdlt.consensus;

import com.google.inject.Inject;
import com.radixdlt.common.AID;
import com.radixdlt.common.Atom;
import com.radixdlt.constraintmachine.CMError;
import com.radixdlt.constraintmachine.DataPointer;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.engine.AtomEventListener;
import com.radixdlt.engine.RadixEngine;
import java.util.List;

/**
 * Executes consensus logic given events
 */
public final class EventCoordinator {
	private final VertexStore vertexStore;
	private final RadixEngine engine;
	private final MemPool memPool;
	private final NetworkSender networkSender;
	private final Pacemaker pacemaker;
	private final SafetyRules safetyRules;

	@Inject
	public EventCoordinator(
		MemPool memPool,
		NetworkSender networkSender,
		SafetyRules safetyRules,
		Pacemaker pacemaker,
		VertexStore vertexStore,
		RadixEngine engine
	) {
		this.memPool = memPool;
		this.networkSender = networkSender;
		this.safetyRules = safetyRules;
		this.pacemaker = pacemaker;
		this.vertexStore = vertexStore;
		this.engine = engine;
	}

	private void newRound() {
		// I am always the leader, bwahaha!
		List<Atom> atoms = memPool.getAtoms(1);
		if (!atoms.isEmpty()) {
			QuorumCertificate highestQC = vertexStore.getHighestQC();
			networkSender.broadcastProposal(new Vertex(highestQC, this.pacemaker.getCurrentRound(), atoms.get(0)));
		}
	}

	public void processVote(Vote vote) {
		// Assume a single node network for now
		QuorumCertificate qc = new QuorumCertificate(vote);
		this.vertexStore.syncToQC(qc);
		this.pacemaker.processQC(qc.getRound());


		// If qc generated, can move to next round
		newRound();
	}

	public void processTimeout() {
		this.pacemaker.processTimeout();

		newRound();
	}

	public void processProposal(Vertex vertex) {
		Atom atom = vertex.getAtom();

		// TODO: Fix this interface
		engine.store(atom, new AtomEventListener() {
			@Override
			public void onCMError(Atom atom, CMError error) {
				memPool.removeRejectedAtom(atom);
			}

			@Override
			public void onStateStore(Atom atom) {
				memPool.removeCommittedAtom(atom);

				vertexStore.insertVertex(vertex);

				final Vote vote = safetyRules.vote(vertex);

				networkSender.sendVote(vote);
			}

			@Override
			public void onVirtualStateConflict(Atom atom, DataPointer issueParticle) {
				memPool.removeRejectedAtom(atom);
			}

			@Override
			public void onStateConflict(Atom atom, DataPointer issueParticle, Atom conflictingAtom) {
				memPool.removeRejectedAtom(atom);
			}

			@Override
			public void onStateMissingDependency(AID atomId, Particle particle) {
				memPool.removeRejectedAtom(atom);
			}
		});
	}
}
