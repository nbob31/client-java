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

import com.epam.reportportal.test.TestUtils;
import com.epam.reportportal.utils.properties.PropertiesLoader;
import org.junit.jupiter.api.Test;

import static com.epam.ta.reportportal.ws.model.launch.Mode.DEBUG;
import static com.epam.ta.reportportal.ws.model.launch.Mode.DEFAULT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ListenerParametersTest {

	@Test
	public void testParseLaunchMode() {
		assertThat(new ListenerParameters().parseLaunchMode("notvalid"), equalTo(DEFAULT));
		assertThat(new ListenerParameters().parseLaunchMode("Debug"), equalTo(DEBUG));
	}

	@Test
	public void testNoNPEs() {
		PropertiesLoader properties = PropertiesLoader.load();
		ListenerParameters listenerParameters = new ListenerParameters(properties);

		assertThat(listenerParameters.getBaseUrl(), nullValue());
		assertThat(listenerParameters.getApiKey(), nullValue());
	}

	@Test
	public void testOnlyApiKeyProvided() {
		PropertiesLoader properties = PropertiesLoader.load("reportportal-api-key.properties");
		ListenerParameters listenerParameters = new ListenerParameters(properties);

		assertEquals("test-api-key", listenerParameters.getApiKey());
	}

	@Test
	public void testOnlyUuidProvided() {
		PropertiesLoader properties = PropertiesLoader.load("reportportal-uuid.properties");
		ListenerParameters listenerParameters = new ListenerParameters(properties);

		assertEquals("test-uuid", listenerParameters.getApiKey());
	}

	@Test
	public void parametersCloneTest() {
		ListenerParameters params = TestUtils.STANDARD_PARAMETERS;
		ListenerParameters paramsClone = params.clone();

		assertThat(paramsClone, not(sameInstance(params)));
		assertThat(paramsClone.getLaunchName(), equalTo(params.getLaunchName()));
		assertThat(paramsClone.getEnable(), equalTo(params.getEnable()));
		assertThat(paramsClone.getClientJoin(), equalTo(params.getClientJoin()));
		assertThat(paramsClone.getBatchLogsSize(), equalTo(params.getBatchLogsSize()));
	}

	/*
	 * We heavily rely on a fact that ListenerParameters is a POJO with no additional hidden logic and has a public constructor with no
	 * parameters. Eg. a clone test above.
	 */
	@Test
	public void parametersShouldHaveEmptyPublicConstructor() throws NoSuchMethodException {
		ListenerParameters.class.getConstructor();
	}
}
