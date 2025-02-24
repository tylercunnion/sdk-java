/*
 * Copyright (C) 2022 Temporal Technologies, Inc. All Rights Reserved.
 *
 * Copyright (C) 2012-2016 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Modifications copyright (C) 2017 Uber Technologies, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this material except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.temporal.internal.worker;

import io.temporal.api.enums.v1.RetryState;
import io.temporal.api.failure.v1.Failure;
import io.temporal.api.workflowservice.v1.RespondActivityTaskCanceledRequest;
import io.temporal.api.workflowservice.v1.RespondActivityTaskCompletedRequest;
import java.time.Duration;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class LocalActivityResult {
  private final @Nonnull String activityId;
  private final int lastAttempt;
  private final @Nullable RespondActivityTaskCompletedRequest executionCompleted;
  private final @Nullable ExecutionFailedResult executionFailed;
  private final @Nullable RespondActivityTaskCanceledRequest executionCanceled;

  static LocalActivityResult completed(ActivityTaskHandler.Result ahResult, int attempt) {
    return new LocalActivityResult(
        ahResult.getActivityId(), attempt, ahResult.getTaskCompleted(), null, null);
  }

  static LocalActivityResult failed(
      String activityId,
      int attempt,
      RetryState retryState,
      Failure timeoutFailure,
      @Nullable Duration backoff) {
    ExecutionFailedResult failedResult =
        new ExecutionFailedResult(retryState, timeoutFailure, backoff);
    return new LocalActivityResult(activityId, attempt, null, failedResult, null);
  }

  static LocalActivityResult cancelled(ActivityTaskHandler.Result ahResult, int attempt) {
    return new LocalActivityResult(
        ahResult.getActivityId(), attempt, null, null, ahResult.getTaskCanceled());
  }

  /**
   * Only zero (manual activity completion) or one request is allowed. Task token and identity
   * fields shouldn't be filled in. Retry options are the service call. These options override the
   * default ones set on the activity worker.
   */
  public LocalActivityResult(
      @Nonnull String activityId,
      int lastAttempt,
      @Nullable RespondActivityTaskCompletedRequest executionCompleted,
      @Nullable ExecutionFailedResult executionFailed,
      @Nullable RespondActivityTaskCanceledRequest executionCanceled) {
    this.activityId = activityId;
    this.lastAttempt = lastAttempt;
    this.executionCompleted = executionCompleted;
    this.executionFailed = executionFailed;
    this.executionCanceled = executionCanceled;
  }

  @Nonnull
  public String getActivityId() {
    return activityId;
  }

  public int getLastAttempt() {
    return lastAttempt;
  }

  @Nullable
  public RespondActivityTaskCompletedRequest getExecutionCompleted() {
    return executionCompleted;
  }

  @Nullable
  public ExecutionFailedResult getExecutionFailed() {
    return executionFailed;
  }

  @Nullable
  public RespondActivityTaskCanceledRequest getExecutionCanceled() {
    return executionCanceled;
  }

  @Override
  public String toString() {
    return "LocalActivityResult{"
        + "activityId='"
        + activityId
        + '\''
        + ", lastAttempt="
        + lastAttempt
        + ", executionCompleted="
        + executionCompleted
        + ", executionFailed="
        + executionFailed
        + ", executionCanceled="
        + executionCanceled
        + '}';
  }

  public static class ExecutionFailedResult {
    @Nonnull private final RetryState retryState;
    @Nonnull private final Failure failure;
    @Nullable private final Duration backoff;

    public ExecutionFailedResult(
        @Nonnull RetryState retryState, @Nonnull Failure failure, @Nullable Duration backoff) {
      this.retryState = retryState;
      this.failure = failure;
      this.backoff = backoff;
    }

    @Nonnull
    public RetryState getRetryState() {
      return retryState;
    }

    @Nonnull
    public Failure getFailure() {
      return failure;
    }

    @Nullable
    public Duration getBackoff() {
      return backoff;
    }

    public boolean isTimeout() {
      return failure.hasTimeoutFailureInfo();
    }

    @Override
    public String toString() {
      return "ExecutionFailedResult{"
          + "retryState="
          + retryState
          + ", failure="
          + failure
          + ", backoff="
          + backoff
          + '}';
    }
  }
}
