package com.radixdlt.tempo.sync.actions;

import com.google.common.collect.ImmutableList;
import com.radixdlt.common.AID;
import com.radixdlt.tempo.sync.SyncAction;
import org.radix.network.peers.Peer;

import java.util.Objects;

public class TimeoutDeliveryRequestAction implements SyncAction {
	private final ImmutableList<AID> aids;
	private final Peer peer;

	public TimeoutDeliveryRequestAction(ImmutableList<AID> aids, Peer peer) {
		this.aids = Objects.requireNonNull(aids, "atom is required");
		this.peer = Objects.requireNonNull(peer, "peer is required");
	}

	public ImmutableList<AID> getAids() {
		return aids;
	}

	public Peer getPeer() {
		return peer;
	}
}
