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

package com.radixdlt;

import com.google.inject.AbstractModule;
import com.radixdlt.consensus.epoch.LocalTimeout;
import com.radixdlt.consensus.liveness.LocalTimeoutSender;
import com.radixdlt.consensus.liveness.PacemakerRx;
import com.radixdlt.utils.ScheduledSenderToRx;
import com.radixdlt.utils.ThreadFactories;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class ConsensusRxModule extends AbstractModule {
	@Override
	protected void configure() {
		ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor(ThreadFactories.daemonThreads("TimeoutSender"));
		ScheduledSenderToRx<LocalTimeout> localTimeouts = new ScheduledSenderToRx<>(ses);
		// Timed local messages
		bind(PacemakerRx.class).toInstance(localTimeouts::messages);
		bind(LocalTimeoutSender.class).toInstance(localTimeouts::scheduleSend);
	}
}