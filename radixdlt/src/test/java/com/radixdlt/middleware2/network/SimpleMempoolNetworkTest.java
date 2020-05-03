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

package com.radixdlt.middleware2.network;

import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.junit.Test;
import org.radix.universe.system.LocalSystem;

import com.radixdlt.atommodel.Atom;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.mempool.messages.MempoolAtomAddedMessage;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.network.addressbook.Peer;
import com.radixdlt.network.messaging.MessageCentral;
import com.radixdlt.network.messaging.MessageListener;
import com.radixdlt.universe.Universe;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.*;

import io.reactivex.rxjava3.observers.TestObserver;

public class SimpleMempoolNetworkTest {

	@Test
	public void testSendMempoolSubmission() {
		Peer peer1 = mock(Peer.class);
		when(peer1.hasSystem()).thenReturn(true);
		when(peer1.getNID()).thenReturn(EUID.ONE);
		Peer peer2 = mock(Peer.class);
		when(peer2.hasSystem()).thenReturn(true);
		when(peer2.getNID()).thenReturn(EUID.TWO);
		LocalSystem system = mock(LocalSystem.class);
		when(system.getNID()).thenReturn(EUID.TWO);
		Universe universe = mock(Universe.class);
		AddressBook addressBook = mock(AddressBook.class);
		when(addressBook.peers()).thenReturn(Stream.of(peer1, peer2));
		MessageCentral messageCentral = mock(MessageCentral.class);
		SimpleMempoolNetwork smn = new SimpleMempoolNetwork(system, universe, addressBook, messageCentral);

		Atom atom = mock(Atom.class);
		smn.sendMempoolSubmission(atom);

		verify(messageCentral, times(1)).send(any(), any());
	}

	@Test
	public void testAtomMessages() {
		LocalSystem system = mock(LocalSystem.class);
		when(system.getNID()).thenReturn(EUID.TWO);
		Universe universe = mock(Universe.class);
		AddressBook addressBook = mock(AddressBook.class);
		MessageCentral messageCentral = mock(MessageCentral.class);
		AtomicReference<MessageListener<MempoolAtomAddedMessage>> callbackRef = new AtomicReference<>();
		doAnswer(inv -> {
			callbackRef.set(inv.getArgument(1));
			return null;
		}).when(messageCentral).addListener(eq(MempoolAtomAddedMessage.class), any());

		SimpleMempoolNetwork smn = new SimpleMempoolNetwork(system, universe, addressBook, messageCentral);

		assertNotNull(callbackRef.get());
		MessageListener<MempoolAtomAddedMessage> callback = callbackRef.get();

		TestObserver<Atom> obs = TestObserver.create();
		smn.atomMessages()
			.subscribe(obs);

		Peer peer = mock(Peer.class);
		Atom atom = mock(Atom.class);
		MempoolAtomAddedMessage message = mock(MempoolAtomAddedMessage.class);
		when(message.atom()).thenReturn(atom);
		callback.handleMessage(peer, message);

		obs.awaitCount(1);
		obs.assertNoErrors();
		obs.assertValue(a -> a == atom);
	}
}