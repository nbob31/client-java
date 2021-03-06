/*
 *  Copyright 2019 EPAM Systems
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.epam.reportportal.service;

import com.epam.reportportal.exception.InternalReportPortalClientException;
import com.epam.reportportal.exception.ReportPortalException;
import com.epam.reportportal.listeners.ItemStatus;
import com.epam.reportportal.listeners.ListenerParameters;
import com.epam.reportportal.service.launch.PrimaryLaunch;
import com.epam.reportportal.service.launch.SecondaryLaunch;
import com.epam.reportportal.utils.SubscriptionUtils;
import com.epam.ta.reportportal.ws.model.ErrorRS;
import com.epam.ta.reportportal.ws.model.FinishExecutionRQ;
import com.epam.ta.reportportal.ws.model.OperationCompletionRS;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import com.epam.ta.reportportal.ws.model.item.ItemCreatedRS;
import com.epam.ta.reportportal.ws.model.launch.LaunchResource;
import com.epam.ta.reportportal.ws.model.launch.StartLaunchRQ;
import io.reactivex.Maybe;
import io.reactivex.Scheduler;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import org.awaitility.Awaitility;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.epam.reportportal.test.TestUtils.simulateStartLaunchResponse;
import static com.epam.reportportal.test.TestUtils.standardLaunchRequest;
import static com.epam.reportportal.utils.SubscriptionUtils.createConstantMaybe;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * @author <a href="mailto:vadzim_hushchanskou@epam.com">Vadzim Hushchanskou</a>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ReportPortalClientJoinTest {
	private static final long WAIT_TIMEOUT = TimeUnit.SECONDS.toMillis(2);

	@Mock
	private ReportPortalClient rpClient;
	@Mock
	private LockFile lockFile;
	private ListenerParameters params;
	private ExecutorService executor;

	@BeforeEach
	public void prepare() {
		params = new ListenerParameters();
		params.setClientJoin(true);
		params.setEnable(Boolean.TRUE);
		executor = Executors.newFixedThreadPool(2);
	}

	@AfterEach
	public void tearDown() throws InterruptedException {
		executor.shutdown();
		if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
			executor.shutdownNow();
		}
	}

	private static void simulateObtainLaunchUuidResponse(final LockFile lockFile) {
		when(lockFile.obtainLaunchUuid(anyString())).then(new Answer<String>() {
			private volatile String firstUuid;

			@Override
			public String answer(InvocationOnMock invocation) {
				if (firstUuid == null) {
					firstUuid = invocation.getArgument(0);
				}
				return firstUuid;
			}
		});
	}

	private static Maybe<LaunchResource> getLaunchResponse(String id) {
		final LaunchResource rs = new LaunchResource();
		rs.setUuid(id);
		return createConstantMaybe(rs);
	}

	private static void simulateGetLaunchResponse(final ReportPortalClient client) {
		when(client.getLaunchByUuid(anyString())).then((Answer<Maybe<LaunchResource>>) invocation -> getLaunchResponse(invocation.getArgument(
				0).toString()));
	}

	private static class StringConsumer implements Consumer<String> {
		private volatile String result;

		@Override
		public void accept(String s) {
			result = s;
		}

		public String getResult() {
			return result;
		}
	}

	private static String getId(Maybe<String> stringMaybe) {
		final StringConsumer consumer = new StringConsumer();
		Disposable disposable = stringMaybe.subscribe(consumer);
		try {
			return Awaitility.await("Waiting for reactivex consumer")
					.pollInterval(2, TimeUnit.MILLISECONDS)
					.atMost(10, TimeUnit.SECONDS)
					.until(new Callable<String>() {
						@Override
						public String call() {
							return consumer.getResult();
						}
					}, Matchers.notNullValue());
		} finally {
			disposable.dispose();
		}
	}

	private static List<Launch> createLaunchesNoStart(int num, ReportPortalClient rpClient, ListenerParameters params, LockFile lockFile,
			ExecutorService executor) {
		List<Launch> result = new ArrayList<>(num);
		simulateObtainLaunchUuidResponse(lockFile);
		for (int i = 0; i < num; i++) {
			ReportPortal rp = new ReportPortal(rpClient, executor, params, lockFile);
			result.add(rp.newLaunch(standardLaunchRequest(params)));
		}
		return result;
	}

	private static List<Launch> createLaunchesNoGetLaunch(int num, ReportPortalClient rpClient, ListenerParameters params,
			LockFile lockFile, ExecutorService executor) {
		simulateStartLaunchResponse(rpClient);
		return createLaunchesNoStart(num, rpClient, params, lockFile, executor);
	}

	private static List<Launch> createLaunches(int num, ReportPortalClient rpClient, ListenerParameters params, LockFile lockFile,
			ExecutorService executor) {
		simulateGetLaunchResponse(rpClient);
		return createLaunchesNoGetLaunch(num, rpClient, params, lockFile, executor);
	}

	@Test
	public void test_two_launches_have_correct_class_names() {
		List<Launch> launches = createLaunchesNoStart(2, rpClient, params, lockFile, executor);

		assertThat(launches.get(0).getClass().getCanonicalName(), Matchers.equalTo(PrimaryLaunch.class.getCanonicalName()));
		assertThat(launches.get(1).getClass().getCanonicalName(), Matchers.equalTo(SecondaryLaunch.class.getCanonicalName()));
	}

	@Test
	public void test_two_launches_call_start_launch_only_once() {
		List<Launch> launches = createLaunches(2, rpClient, params, lockFile, executor);
		launches.get(0).start();
		launches.get(1).start();

		verify(lockFile, times(2)).obtainLaunchUuid(ArgumentMatchers.anyString());
		verify(rpClient, after(WAIT_TIMEOUT).times(1)).startLaunch(any(StartLaunchRQ.class));
	}

	@Test
	public void test_primary_launch_start_launch_request() {
		List<Launch> launches = createLaunchesNoGetLaunch(1, rpClient, params, lockFile, executor);
		launches.get(0).start();

		ArgumentCaptor<String> passedUuid = ArgumentCaptor.forClass(String.class);
		verify(lockFile, timeout(WAIT_TIMEOUT).times(1)).obtainLaunchUuid(passedUuid.capture());

		ArgumentCaptor<StartLaunchRQ> sentUuid = ArgumentCaptor.forClass(StartLaunchRQ.class);
		verify(rpClient, timeout(WAIT_TIMEOUT).times(1)).startLaunch(sentUuid.capture());

		StartLaunchRQ startLaunch = sentUuid.getValue();

		assertThat(startLaunch.getUuid(), equalTo(passedUuid.getValue()));
	}

	@Test
	public void test_two_launches_have_same_uuid() {
		List<Launch> launches = createLaunches(2, rpClient, params, lockFile, executor);

		String firstUuid = getId(launches.get(0).start());
		String secondUuid = getId(launches.get(1).start());

		assertThat(firstUuid, equalTo(secondUuid));
	}

	private static FinishExecutionRQ standardLaunchFinish() {
		FinishExecutionRQ rq = new FinishExecutionRQ();
		rq.setEndTime(Calendar.getInstance().getTime());
		rq.setStatus(ItemStatus.PASSED.name());
		return rq;
	}

	@Test
	public void test_only_primary_launch_finish_launch_on_rp() {
		List<Launch> launches = createLaunches(2, rpClient, params, lockFile, executor);
		launches.get(0).start();
		launches.get(1).start();

		when(rpClient.finishLaunch(any(), any(FinishExecutionRQ.class))).thenReturn(createConstantMaybe(new OperationCompletionRS()));

		launches.get(0).finish(standardLaunchFinish());
		launches.get(1).finish(standardLaunchFinish());

		verify(lockFile, times(2)).finishInstanceUuid(ArgumentMatchers.anyString());
		verify(rpClient, after(WAIT_TIMEOUT).times(1)).finishLaunch(anyString(), any(FinishExecutionRQ.class));
	}

	@Test
	public void test_standard_launch_returned_if_the_feature_is_off() {
		params.setClientJoin(false);
		ReportPortal rp1 = new ReportPortal(rpClient, executor, params, null);
		ReportPortal rp2 = new ReportPortal(rpClient, executor, params, null);

		Launch firstLaunch = rp1.newLaunch(standardLaunchRequest(params));
		Launch secondLaunch = rp2.newLaunch(standardLaunchRequest(params));

		assertThat(firstLaunch.getClass().getCanonicalName(), equalTo(LaunchImpl.class.getCanonicalName()));
		assertThat(secondLaunch.getClass().getCanonicalName(), equalTo(LaunchImpl.class.getCanonicalName()));
	}

	@Test
	public void test_rp_client_throws_error_in_case_of_lock_file_error() {
		ReportPortal rp1 = new ReportPortal(rpClient, executor, params, lockFile);

		InternalReportPortalClientException exc = Assertions.assertThrows(InternalReportPortalClientException.class,
				() -> rp1.newLaunch(standardLaunchRequest(params))
		);
		assertThat(exc.getMessage(), equalTo("Unable to create a new launch: unable to read/write lock file."));
	}

	private static StartTestItemRQ standardItemRequest() {
		StartTestItemRQ rq = new StartTestItemRQ();
		rq.setName("unit-test suite");
		rq.setStartTime(Calendar.getInstance().getTime());
		rq.setType("SUITE");
		return rq;
	}

	private static void simulateStartItemResponse(final ReportPortalClient client, final String itemUuid) {
		when(client.startTestItem(any(StartTestItemRQ.class))).then((Answer<Maybe<ItemCreatedRS>>) invocation -> standardItemResponse(
				itemUuid));
	}

	private static Maybe<ItemCreatedRS> standardItemResponse(String id) {
		ItemCreatedRS rs = new ItemCreatedRS();
		rs.setId(id);
		return createConstantMaybe(rs);
	}

	@Test
	public void test_rp_client_sends_correct_start_item_for_secondary_launch() {
		List<Launch> launches = createLaunches(2, rpClient, params, lockFile, executor);
		launches.get(0).start();
		launches.get(1).start();

		String itemUuid = UUID.randomUUID().toString();
		simulateStartItemResponse(rpClient, itemUuid);
		Maybe<String> rs = launches.get(1).startTestItem(standardItemRequest());
		String testItemId = getId(rs);

		assertThat(testItemId, equalTo(itemUuid));
	}

	@Test
	public void test_secondary_launch_call_get_launch_by_uuid() {
		List<Launch> launches = createLaunchesNoStart(2, rpClient, params, lockFile, executor);
		simulateGetLaunchResponse(rpClient);
		launches.get(1).start();

		ArgumentCaptor<String> obtainUuids = ArgumentCaptor.forClass(String.class);
		verify(lockFile, timeout(WAIT_TIMEOUT).times(2)).obtainLaunchUuid(obtainUuids.capture());

		ArgumentCaptor<String> sentUuid = ArgumentCaptor.forClass(String.class);
		verify(rpClient, after(WAIT_TIMEOUT * 2).times(1)).getLaunchByUuid(sentUuid.capture());

		assertThat(sentUuid.getValue(), equalTo(obtainUuids.getAllValues().get(0)));
	}

	private static Maybe<LaunchResource> getLaunchErrorResponse() {
		return SubscriptionUtils.createConstantMaybe(new ReportPortalException(404, "Launch not found", new ErrorRS()));
	}

	private static void simulateGetLaunchByUuidResponse(ReportPortalClient client) {
		Answer<Maybe<LaunchResource>> errorAnswer = invocation -> getLaunchErrorResponse();
		when(client.getLaunchByUuid(anyString())).then(errorAnswer)
				.then(errorAnswer)
				.then((Answer<Maybe<LaunchResource>>) invocation -> getLaunchResponse(invocation.getArgument(0).toString()));
	}

	@Test
	public void test_secondary_launch_awaits_get_launch_by_uuid_correct_response_for_v1() {
		int num = 2;
		simulateObtainLaunchUuidResponse(lockFile);
		simulateGetLaunchByUuidResponse(rpClient);
		params.setAsyncReporting(false);
		List<Launch> launches = new ArrayList<Launch>(num);
		for (int i = 0; i < num; i++) {
			ReportPortal rp = new ReportPortal(rpClient, executor, params, lockFile);
			launches.add(rp.newLaunch(standardLaunchRequest(params)));
		}
		launches.get(1).start();

		verify(lockFile, timeout(WAIT_TIMEOUT).times(2)).obtainLaunchUuid(anyString());
		verify(rpClient, after(WAIT_TIMEOUT * 3).times(3)).getLaunchByUuid(anyString());
	}

	@Test
	public void test_secondary_launch_does_not_wait_get_launch_by_uuid_correct_response_for_v2() {
		int num = 2;
		simulateObtainLaunchUuidResponse(lockFile);
		params.setAsyncReporting(true);
		List<Launch> launches = new ArrayList<Launch>(num);
		for (int i = 0; i < num; i++) {
			ReportPortal rp = new ReportPortal(rpClient, executor, params, lockFile);
			launches.add(rp.newLaunch(standardLaunchRequest(params)));
		}
		launches.get(1).start();

		verify(rpClient, after(WAIT_TIMEOUT).times(0)).getLaunchByUuid(anyString());
	}

	@Test
	public void test_two_launches_have_the_same_executors() {
		List<Launch> launches = createLaunchesNoStart(2, rpClient, params, lockFile, executor);

		assertThat(((LaunchImpl) launches.get(0)).getExecutor(), sameInstance(executor));
		assertThat(((LaunchImpl) launches.get(1)).getExecutor(), sameInstance(executor));
	}

	@Test
	public void test_two_launches_have_the_same_scheduler() {
		List<Launch> launches = createLaunchesNoStart(2, rpClient, params, lockFile, executor);

		Scheduler scheduler1 = ((LaunchImpl) launches.get(0)).getScheduler();
		Scheduler scheduler2 = ((LaunchImpl) launches.get(1)).getScheduler();
		assertThat(scheduler1, sameInstance(scheduler2));
	}
}
