/*
 *  Copyright (C) 2020 Temporal Technologies, Inc. All Rights Reserved.
 *
 *  Copyright 2012-2016 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 *  Modifications copyright (C) 2017 Uber Technologies, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"). You may not
 *  use this file except in compliance with the License. A copy of the License is
 *  located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 *  or in the "license" file accompanying this file. This file is distributed on
 *  an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *  express or implied. See the License for the specific language governing
 *  permissions and limitations under the License.
 */

package io.temporal.internal.replay;

import static io.temporal.testUtils.HistoryUtils.*;
import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.*;

import com.uber.m3.tally.NoopScope;
import com.uber.m3.tally.RootScopeBuilder;
import com.uber.m3.tally.Scope;
import com.uber.m3.util.Duration;
import com.uber.m3.util.ImmutableMap;
import io.temporal.api.common.v1.Payloads;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.api.history.v1.HistoryEvent;
import io.temporal.api.query.v1.WorkflowQuery;
import io.temporal.api.workflowservice.v1.PollForDecisionTaskResponse;
import io.temporal.common.reporter.TestStatsReporter;
import io.temporal.internal.metrics.MetricsTag;
import io.temporal.internal.metrics.MetricsType;
import io.temporal.internal.testservice.TestWorkflowService;
import io.temporal.internal.worker.SingleWorkerOptions;
import io.temporal.internal.worker.WorkflowExecutionException;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.testUtils.HistoryUtils;
import io.temporal.worker.WorkflowImplementationOptions;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import junit.framework.TestCase;
import org.junit.Before;
import org.junit.Test;

public class ReplayDeciderCacheTests {

  private Scope metricsScope;
  private TestStatsReporter reporter;

  @Before
  public void setUp() {
    reporter = new TestStatsReporter();
    metricsScope = new RootScopeBuilder().reporter(reporter).reportEvery(Duration.ofMillis(10));
  }

  @Test
  public void whenHistoryIsFullNewReplayDeciderIsReturnedAndCached_InitiallyEmpty()
      throws Exception {
    // Arrange
    DeciderCache replayDeciderCache = new DeciderCache(10, new NoopScope());
    PollForDecisionTaskResponse decisionTask =
        HistoryUtils.generateDecisionTaskWithInitialHistory();

    String runId = decisionTask.getWorkflowExecution().getRunId();

    assertCacheIsEmpty(replayDeciderCache, runId);

    // Act
    Decider decider =
        replayDeciderCache.getOrCreate(
            decisionTask, metricsScope, () -> createFakeDecider(decisionTask));

    // Assert
    assertNotEquals(
        decider,
        replayDeciderCache.getOrCreate(
            decisionTask, metricsScope, () -> createFakeDecider(decisionTask)));
  }

  @Test
  public void whenHistoryIsFullNewReplayDeciderIsReturned_InitiallyCached() throws Exception {
    TestWorkflowService testService = new TestWorkflowService();
    WorkflowServiceStubs service = testService.newClientStub();

    // Arrange
    DeciderCache replayDeciderCache = new DeciderCache(10, new NoopScope());
    PollForDecisionTaskResponse decisionTask1 =
        HistoryUtils.generateDecisionTaskWithInitialHistory(
            "namespace", "taskQueue", "workflowType", service);

    Decider decider =
        replayDeciderCache.getOrCreate(
            decisionTask1, metricsScope, () -> createFakeDecider(decisionTask1));
    replayDeciderCache.addToCache(decisionTask1, decider);

    PollForDecisionTaskResponse decisionTask2 =
        HistoryUtils.generateDecisionTaskWithPartialHistoryFromExistingTask(
            decisionTask1, "namespace", "stickyTaskQueue", service);

    assertEquals(
        decider,
        replayDeciderCache.getOrCreate(
            decisionTask2, metricsScope, () -> doNotCreateFakeDecider(decisionTask2)));

    // Act
    Decider decider2 =
        replayDeciderCache.getOrCreate(
            decisionTask2, metricsScope, () -> createFakeDecider(decisionTask2));

    // Assert
    assertEquals(
        decider2,
        replayDeciderCache.getOrCreate(
            decisionTask2, metricsScope, () -> createFakeDecider(decisionTask2)));
    assertSame(decider2, decider);
    service.shutdownNow();
    try {
      service.awaitTermination(1, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  @Test(timeout = 2000)
  public void whenHistoryIsPartialCachedEntryIsReturned() throws Exception {
    // Arrange
    Map<String, String> tags =
        new ImmutableMap.Builder<String, String>(2)
            .put(MetricsTag.NAMESPACE, "namespace")
            .put(MetricsTag.TASK_QUEUE, "stickyTaskQueue")
            .build();
    Scope scope = metricsScope.tagged(tags);

    DeciderCache replayDeciderCache = new DeciderCache(10, scope);
    TestWorkflowService testService = new TestWorkflowService(true);
    WorkflowServiceStubs service = testService.newClientStub();
    try {
      PollForDecisionTaskResponse decisionTask =
          HistoryUtils.generateDecisionTaskWithInitialHistory(
              "namespace", "taskQueue", "workflowType", service);

      Decider decider =
          replayDeciderCache.getOrCreate(
              decisionTask, scope, () -> createFakeDecider(decisionTask));
      replayDeciderCache.addToCache(decisionTask, decider);

      // Act
      PollForDecisionTaskResponse decisionTask2 =
          HistoryUtils.generateDecisionTaskWithPartialHistoryFromExistingTask(
              decisionTask, "namespace", "stickyTaskQueue", service);
      Decider decider2 =
          replayDeciderCache.getOrCreate(
              decisionTask2, scope, () -> doNotCreateFakeDecider(decisionTask2));

      // Assert
      // Wait for reporter
      Thread.sleep(100);
      reporter.assertCounter(MetricsType.STICKY_CACHE_HIT, tags, 1);

      assertEquals(decider, decider2);
    } finally {
      service.shutdownNow();
      service.awaitTermination(1, TimeUnit.SECONDS);
      testService.close();
    }
  }

  @Test
  public void whenHistoryIsPartialAndCacheIsEmptyThenExceptionIsThrown() throws Exception {
    // Arrange
    Map<String, String> tags =
        new ImmutableMap.Builder<String, String>(2)
            .put(MetricsTag.NAMESPACE, "namespace")
            .put(MetricsTag.TASK_QUEUE, "stickyTaskQueue")
            .build();
    Scope scope = metricsScope.tagged(tags);
    DeciderCache replayDeciderCache = new DeciderCache(10, scope);

    // Act
    PollForDecisionTaskResponse decisionTask =
        HistoryUtils.generateDecisionTaskWithPartialHistory();

    try {
      replayDeciderCache.getOrCreate(decisionTask, scope, () -> createFakeDecider(decisionTask));
      fail(
          "Expected replayDeciderCache.getOrCreate to throw IllegalArgumentException but no exception was thrown");
    } catch (IllegalArgumentException ex) {

      // Wait for reporter
      Thread.sleep(100);
      reporter.assertCounter(MetricsType.STICKY_CACHE_MISS, tags, 1);
    }
  }

  @Test
  public void evictAnyWillInvalidateAnEntryRandomlyFromTheCache() throws Exception {
    Map<String, String> tags =
        new ImmutableMap.Builder<String, String>(2)
            .put(MetricsTag.NAMESPACE, NAMESPACE)
            .put(MetricsTag.TASK_QUEUE, HOST_TASK_QUEUE)
            .put(MetricsTag.WORKFLOW_TYPE, WORKFLOW_TYPE)
            .build();
    Scope scope = metricsScope.tagged(tags);

    // Arrange
    DeciderCache replayDeciderCache = new DeciderCache(50, scope);
    PollForDecisionTaskResponse decisionTask1 =
        HistoryUtils.generateDecisionTaskWithInitialHistory();
    PollForDecisionTaskResponse decisionTask2 =
        HistoryUtils.generateDecisionTaskWithInitialHistory();
    PollForDecisionTaskResponse decisionTask3 =
        HistoryUtils.generateDecisionTaskWithInitialHistory();

    // Act
    Decider decider =
        replayDeciderCache.getOrCreate(
            decisionTask1, scope, () -> createFakeDecider(decisionTask1));
    replayDeciderCache.addToCache(decisionTask1, decider);
    decider =
        replayDeciderCache.getOrCreate(
            decisionTask2, scope, () -> createFakeDecider(decisionTask2));
    replayDeciderCache.addToCache(decisionTask2, decider);
    decider =
        replayDeciderCache.getOrCreate(
            decisionTask3, scope, () -> createFakeDecider(decisionTask3));
    replayDeciderCache.addToCache(decisionTask3, decider);

    assertEquals(3, replayDeciderCache.size());

    replayDeciderCache.evictAnyNotInProcessing(
        decisionTask3.getWorkflowExecution().getRunId(), scope);

    // Assert
    assertEquals(2, replayDeciderCache.size());

    // Wait for reporter
    Thread.sleep(100);
    reporter.assertCounter(MetricsType.STICKY_CACHE_TOTAL_FORCED_EVICTION, tags, 3);
  }

  @Test
  public void evictAnyWillNotInvalidateItself() throws Exception {
    // Arrange
    DeciderCache replayDeciderCache = new DeciderCache(50, new NoopScope());
    PollForDecisionTaskResponse decisionTask1 =
        HistoryUtils.generateDecisionTaskWithInitialHistory();

    // Act
    Decider decider =
        replayDeciderCache.getOrCreate(
            decisionTask1, metricsScope, () -> createFakeDecider(decisionTask1));
    replayDeciderCache.addToCache(decisionTask1, decider);

    assertEquals(1, replayDeciderCache.size());

    replayDeciderCache.evictAnyNotInProcessing(
        decisionTask1.getWorkflowExecution().getRunId(), metricsScope);

    // Assert
    assertEquals(1, replayDeciderCache.size());
  }

  private void assertCacheIsEmpty(DeciderCache cache, String runId) throws Exception {
    Throwable ex = null;
    try {
      PollForDecisionTaskResponse decisionTask =
          PollForDecisionTaskResponse.newBuilder()
              .setWorkflowExecution(WorkflowExecution.newBuilder().setRunId(runId))
              .build();
      cache.getOrCreate(decisionTask, metricsScope, () -> doNotCreateFakeDecider(decisionTask));
    } catch (AssertionError e) {
      ex = e;
    }
    TestCase.assertNotNull(ex);
  }

  private ReplayDecider doNotCreateFakeDecider(
      @SuppressWarnings("unused") PollForDecisionTaskResponse response) {
    fail("should not be called");
    return null;
  }

  private ReplayDecider createFakeDecider(PollForDecisionTaskResponse response) {
    return new ReplayDecider(
        null,
        "namespace",
        new ReplayWorkflow() {
          @Override
          public void start(HistoryEvent event, DecisionContext context) {}

          @Override
          public void handleSignal(String signalName, Optional<Payloads> input, long eventId) {}

          @Override
          public boolean eventLoop() throws Throwable {
            return false;
          }

          @Override
          public Optional<Payloads> getOutput() {
            return Optional.empty();
          }

          @Override
          public void cancel(String reason) {}

          @Override
          public void close() {}

          @Override
          public long getNextWakeUpTime() {
            return 0;
          }

          @Override
          public Optional<Payloads> query(WorkflowQuery query) {
            return Optional.empty();
          }

          @Override
          public WorkflowExecutionException mapUnexpectedException(Throwable failure) {
            return null;
          }

          @Override
          public WorkflowExecutionException mapError(Error failure) {
            return null;
          }

          @Override
          public WorkflowImplementationOptions getWorkflowImplementationOptions() {
            return WorkflowImplementationOptions.newBuilder().build();
          }
        },
        response.toBuilder(),
        SingleWorkerOptions.newBuilder().build(),
        metricsScope,
        (a, d) -> true);
  }
}
