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

package org.radix.serialization;

import com.radixdlt.atommodel.Atom;
import com.radixdlt.atommodel.message.MessageParticle;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.View;
import com.radixdlt.consensus.VoteData;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECDSASignatures;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.Hash;
import com.radixdlt.identifiers.RadixAddress;

public class ProposalSerializeTest extends SerializeObject<Proposal> {
	public ProposalSerializeTest() {
		super(Proposal.class, ProposalSerializeTest::get);
	}

	private static Proposal get() {
		View view = View.of(1234567891L);
		Hash id = Hash.random();

		VertexMetadata vertexMetadata = new VertexMetadata(view, id);
		VertexMetadata parent = new VertexMetadata(View.of(1234567890L), Hash.random());
		VoteData voteData = new VoteData(vertexMetadata, parent);

		QuorumCertificate qc = new QuorumCertificate(voteData, new ECDSASignatures());

		RadixAddress address = RadixAddress.from("JH1P8f3znbyrDj8F4RWpix7hRkgxqHjdW2fNnKpR3v6ufXnknor");
		Atom atom = new Atom();
		// add a particle to ensure atom is valid and has at least one shard
		atom.addParticleGroupWith(new MessageParticle(address, address, "Hello".getBytes()), Spin.UP);
		Vertex vertex = Vertex.createVertex(qc, view, atom);


		return new Proposal(vertex, ECKeyPair.generateNew().getPublicKey(), new ECDSASignature());
	}
}