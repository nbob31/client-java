/*
 * Copyright 2019 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.epam.reportportal.listeners;

/**
 * Possible statuses of finished test methods.
 * @deprecated use {@link ItemStatus} enumeration instead of this class. Since a backend does not currently support custom statuses.
 */
@Deprecated
public final class Statuses {

	private Statuses() {

	}

	public static final String PASSED = ItemStatus.PASSED.name();
	public static final String FAILED = ItemStatus.FAILED.name();
	public static final String SKIPPED = ItemStatus.SKIPPED.name();
}
