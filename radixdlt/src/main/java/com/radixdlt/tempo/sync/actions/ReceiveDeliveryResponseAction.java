package com.radixdlt.tempo.sync.actions;

import com.radixdlt.tempo.TempoAtom;
import com.radixdlt.tempo.sync.SyncAction;
import com.radixdlt.tempo.sync.messages.DeliveryResponseMessage;
import org.radix.network.peers.Peer;

import java.util.Objects;

public class ReceiveDeliveryResponseAction implements SyncAction {
	private final TempoAtom atom;
	private final Peer peer;

	public ReceiveDeliveryResponseAction(TempoAtom atom, Peer peer) {
		this.atom = Objects.requireNonNull(atom, "atom is required");
		this.peer = Objects.requireNonNull(peer, "peer is required");
	}

	public TempoAtom getAtom() {
		return atom;
	}

	public Peer getPeer() {
		return peer;
	}

	public DeliveryResponseMessage toMessage() {
		return new DeliveryResponseMessage(atom);
	}

	public static ReceiveDeliveryResponseAction from(DeliveryResponseMessage message, Peer peer) {
		return new ReceiveDeliveryResponseAction(message.getAtom(), peer);
	}
}
