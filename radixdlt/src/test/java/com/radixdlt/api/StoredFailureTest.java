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

package com.radixdlt.api;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.middleware2.CommittedAtom;
import org.junit.Before;
import org.junit.Test;

public class StoredFailureTest {
	private CommittedAtom committedAtom;
	private RadixEngineException exception;
	private StoredFailure storedFailure;

	@Before
	public void setUp() {
		this.committedAtom = mock(CommittedAtom.class);
		this.exception = mock(RadixEngineException.class);
		this.storedFailure = new StoredFailure(committedAtom, exception);
	}

	@Test
	public void testGetters() {
		assertEquals(this.committedAtom, this.storedFailure.getAtom());
		assertEquals(this.exception, this.storedFailure.getException());
	}


	@Test
	public void testToString() {
		assertThat(this.exception.toString()).isNotNull();
	}
}