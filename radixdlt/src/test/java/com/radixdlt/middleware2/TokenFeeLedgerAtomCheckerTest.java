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

package com.radixdlt.middleware2;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.DefaultSerialization;
import com.radixdlt.atommodel.Atom;
import com.radixdlt.atommodel.message.MessageParticle;
import com.radixdlt.atommodel.tokens.UnallocatedTokensParticle;
import com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle.TokenTransition;
import com.radixdlt.atommodel.tokens.TokenPermission;
import com.radixdlt.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.atommodel.unique.UniqueParticle;
import com.radixdlt.constraintmachine.CMInstruction;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.Hash;
import com.radixdlt.fees.FeeTable;
import com.radixdlt.fees.PerParticleFeeEntry;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.middleware.ParticleGroup;
import com.radixdlt.middleware.SpunParticle;
import com.radixdlt.middleware2.ClientAtom.LedgerAtomConversionException;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.utils.UInt256;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TokenFeeLedgerAtomCheckerTest {
	private TokenFeeLedgerAtomChecker checker;
	private RRI rri;

	@Before
	public void setUp() {
		PerParticleFeeEntry feeEntry = PerParticleFeeEntry.of(UniqueParticle.class, 0, UInt256.TEN);
		FeeTable feeTable = FeeTable.from(UInt256.ZERO, ImmutableList.of(feeEntry));
		RadixAddress address = new RadixAddress((byte) 0, ECKeyPair.generateNew().getPublicKey());
		this.rri = RRI.of(address, "TESTTOKEN");
		Serialization serialization = DefaultSerialization.getInstance();
		this.checker = new TokenFeeLedgerAtomChecker(feeTable, rri, serialization);
	}

	@Test
	public void when_validating_atom_with_particles__result_has_no_error() throws LedgerAtomConversionException {
		byte[] bytes = new byte[] {
			0, 1
		};
		RadixAddress address = new RadixAddress((byte) 0, ECKeyPair.generateNew().getPublicKey());
		MessageParticle particle = new MessageParticle(address, address, bytes);
		List<ParticleGroup> particleGroups = ImmutableList.of(ParticleGroup.of(ImmutableList.of(SpunParticle.up(particle))));
		Atom atom = new Atom(particleGroups, ImmutableMap.of(), ImmutableMap.of());
		ClientAtom ledgerAtom = ClientAtom.convertFromApiAtom(atom);
		assertThat(checker.check(ledgerAtom).isSuccess()).isTrue();
	}

	@Test
	public void when_validating_atom_without_particles__result_has_error() {
		ClientAtom ledgerAtom = mock(ClientAtom.class);
		CMInstruction cmInstruction = new CMInstruction(
			ImmutableList.of(), Hash.random(), ImmutableMap.of()
		);
		when(ledgerAtom.getAID()).thenReturn(mock(AID.class));
		when(ledgerAtom.getCMInstruction()).thenReturn(cmInstruction);
		when(ledgerAtom.getMetaData()).thenReturn(ImmutableMap.of("timestamp", "0"));

		assertThat(checker.check(ledgerAtom).getErrorMessage())
			.contains("instructions");
	}

	@Test
	public void when_validating_atom_without_fee__result_has_error() throws LedgerAtomConversionException {
		RadixAddress address = new RadixAddress((byte) 0, ECKeyPair.generateNew().getPublicKey());
		UniqueParticle particle = new UniqueParticle("FOO", address, 0L);
		List<ParticleGroup> particleGroups = ImmutableList.of(ParticleGroup.of(ImmutableList.of(SpunParticle.up(particle))));
		Atom atom = new Atom(particleGroups, ImmutableMap.of(), ImmutableMap.of());
		ClientAtom ledgerAtom = ClientAtom.convertFromApiAtom(atom);

		assertThat(checker.check(ledgerAtom).getErrorMessage())
			.contains("less than required minimum");
	}

	@Test
	public void when_validating_atom_with_fee__result_has_no_error() throws LedgerAtomConversionException {
		RadixAddress address = new RadixAddress((byte) 0, ECKeyPair.generateNew().getPublicKey());
		UniqueParticle particle1 = new UniqueParticle("FOO", address, 0L);
		UnallocatedTokensParticle particle2 = new UnallocatedTokensParticle(
			UInt256.TEN,
			UInt256.ONE,
			this.rri,
			ImmutableMap.of(
				TokenTransition.BURN, TokenPermission.ALL,
				TokenTransition.MINT, TokenPermission.ALL
			)
		);
		List<ParticleGroup> particleGroups = ImmutableList.of(
			ParticleGroup.of(ImmutableList.of(SpunParticle.up(particle1))),
			ParticleGroup.of(ImmutableList.of(SpunParticle.up(particle2)))
		);
		Atom atom = new Atom(particleGroups, ImmutableMap.of(), ImmutableMap.of());
		ClientAtom ledgerAtom = ClientAtom.convertFromApiAtom(atom);

		assertThat(checker.check(ledgerAtom).isSuccess()).isTrue();
	}

	@Test
	public void when_validating_atom_with_fee_and_change__result_has_no_error() throws LedgerAtomConversionException {
		RadixAddress address = new RadixAddress((byte) 0, ECKeyPair.generateNew().getPublicKey());
		UniqueParticle particle1 = new UniqueParticle("FOO", address, 0L);
		UnallocatedTokensParticle particle2 = new UnallocatedTokensParticle(
				UInt256.TEN,
				UInt256.ONE,
				this.rri,
				ImmutableMap.of(
					TokenTransition.BURN, TokenPermission.ALL,
					TokenTransition.MINT, TokenPermission.ALL
				)
			);
		TransferrableTokensParticle particle3 = new TransferrableTokensParticle(
				address,
				UInt256.ONE,
				UInt256.ONE,
				this.rri,
				ImmutableMap.of(
					TokenTransition.BURN, TokenPermission.ALL,
					TokenTransition.MINT, TokenPermission.ALL
				)
			);
		List<ParticleGroup> particleGroups = ImmutableList.of(
			ParticleGroup.of(ImmutableList.of(SpunParticle.up(particle1))),
			ParticleGroup.of(ImmutableList.of(SpunParticle.up(particle2), SpunParticle.up(particle3)))
		);
		Atom atom = new Atom(particleGroups, ImmutableMap.of(), ImmutableMap.of());
		ClientAtom ledgerAtom = ClientAtom.convertFromApiAtom(atom);

		assertThat(checker.check(ledgerAtom).isSuccess()).isTrue();
	}

	@Test
	public void when_validating_atom_with_fee_and_metadata__result_has_error() throws LedgerAtomConversionException {
		RadixAddress address = new RadixAddress((byte) 0, ECKeyPair.generateNew().getPublicKey());
		UniqueParticle particle1 = new UniqueParticle("FOO", address, 0L);
		UnallocatedTokensParticle particle2 = new UnallocatedTokensParticle(
			UInt256.TEN,
			UInt256.ONE,
			this.rri,
			ImmutableMap.of(
				TokenTransition.BURN, TokenPermission.ALL,
				TokenTransition.MINT, TokenPermission.ALL
			)
		);
		List<ParticleGroup> particleGroups = ImmutableList.of(
			ParticleGroup.of(ImmutableList.of(SpunParticle.up(particle1))),
			ParticleGroup.of(ImmutableList.of(SpunParticle.up(particle2)), ImmutableMap.of("foo", "bar"))
		);
		Atom atom = new Atom(particleGroups, ImmutableMap.of(), ImmutableMap.of());
		ClientAtom ledgerAtom = ClientAtom.convertFromApiAtom(atom);

		assertThat(checker.check(ledgerAtom).getErrorMessage())
			.contains("less than required minimum");
	}
}