/*
 * Copyright (c) 2011-2019 Pivotal Software Inc, All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package reactor.core.publisher;

import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiFunction;

import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.scheduler.Scheduler;

class TrackingExecutorServiceDecorator
		implements BiFunction<Scheduler, ScheduledExecutorService, ScheduledExecutorService> {

	private final Collection<Tracker> trackers;

	TrackingExecutorServiceDecorator(Collection<Tracker> trackers) {
		this.trackers = trackers;
	}

	@Override
	public ScheduledExecutorService apply(Scheduler scheduler, ScheduledExecutorService service) {
		return new TaskWrappingScheduledExecutorService(service) {

			@Override
			protected Runnable wrap(Runnable runnable) {
				Tracker.Marker marker = Tracker.Marker.CURRENT.get();
				if (marker == null) {
					return runnable;
				}

				return () -> {
					Tracker.Marker.CURRENT.set(marker);
					try {
						Disposable.Composite composite = Disposables.composite();

						for (Tracker tracker : trackers) {
							Disposable disposable = tracker.onScopePassing(marker);
							composite.add(disposable);
						}

						try {
							runnable.run();
						}
						finally {
							composite.dispose();
						}
					}
					finally {
						Tracker.Marker.CURRENT.remove();
					}
				};
			}

			@Override
			protected <V> Callable<V> wrap(Callable<V> callable) {
				Tracker.Marker marker = Tracker.Marker.CURRENT.get();
				if (marker == null) {
					return callable;
				}

				return () -> {
					Tracker.Marker.CURRENT.set(marker);
					try {
						Disposable.Composite composite = Disposables.composite();

						for (Tracker tracker : trackers) {
							Disposable disposable = tracker.onScopePassing(marker);
							composite.add(disposable);
						}

						try {
							return callable.call();
						}
						finally {
							composite.dispose();
						}
					}
					finally {
						Tracker.Marker.CURRENT.remove();
					}
				};
			}
		};
	}
}