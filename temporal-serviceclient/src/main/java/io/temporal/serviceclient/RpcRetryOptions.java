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

package io.temporal.serviceclient;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.protobuf.GeneratedMessageV3;
import io.grpc.Status;
import io.temporal.internal.common.OptionsUtils;
import io.temporal.serviceclient.rpcretry.DefaultStubServiceOperationRpcRetryOptions;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class RpcRetryOptions {

  public static Builder newBuilder() {
    return new Builder();
  }

  public static Builder newBuilder(RpcRetryOptions options) {
    return new Builder(options);
  }

  public static RpcRetryOptions getDefaultInstance() {
    return DEFAULT_INSTANCE;
  }

  private static final RpcRetryOptions DEFAULT_INSTANCE;

  static {
    DEFAULT_INSTANCE = RpcRetryOptions.newBuilder().build();
  }

  public static class DoNotRetryItem {
    private final Status.Code code;
    private final Class<? extends GeneratedMessageV3> detailsClass;

    /**
     * @param code errors with this code will be considered non retryable. {@link
     *     Status.Code#CANCELLED} and {@link Status.Code#DEADLINE_EXCEEDED} are always considered
     *     non-retryable.
     * @param detailsClass If not null, only failures with the {@code code} and details of this
     *     {@code detailsClass} class are non retryable If null, all failures with the code are non
     *     retryable.
     */
    public DoNotRetryItem(
        @Nonnull Status.Code code, @Nullable Class<? extends GeneratedMessageV3> detailsClass) {
      this.code = Preconditions.checkNotNull(code, "code");
      this.detailsClass = detailsClass;
    }

    public Status.Code getCode() {
      return code;
    }

    public Class<? extends GeneratedMessageV3> getDetailsClass() {
      return detailsClass;
    }
  }

  public static final class Builder {

    private Duration initialInterval;

    private Duration congestionInitialInterval;

    private Duration expiration;

    private double backoffCoefficient;

    private int maximumAttempts;

    private Duration maximumInterval;

    // 0 is a valid value for maximumJitterCoefficient, and yet, it is not the default value for
    // maximumJitterCoefficient. Thus, use -1 instead as a marker for "use default value"
    private double maximumJitterCoefficient = -1.0;

    private List<DoNotRetryItem> doNotRetry = new ArrayList<>();

    private Builder() {}

    private Builder(RpcRetryOptions options) {
      if (options == null) {
        return;
      }
      this.backoffCoefficient = options.getBackoffCoefficient();
      this.maximumAttempts = options.getMaximumAttempts();
      this.expiration = options.getExpiration();
      this.initialInterval = options.getInitialInterval();
      this.congestionInitialInterval = options.getCongestionInitialInterval();
      this.maximumInterval = options.getMaximumInterval();
      this.maximumJitterCoefficient = options.getMaximumJitterCoefficient();
      this.doNotRetry = options.getDoNotRetry();
    }

    /**
     * Interval of the first retry, on regular failures. If coefficient is 1.0 then it is used for
     * all retries. Defaults to 50ms.
     *
     * @param initialInterval Interval to wait on first retry. Default will be used if set to {@code
     *     null}.
     */
    public Builder setInitialInterval(Duration initialInterval) {
      if (initialInterval != null) {
        if (initialInterval.isNegative() || initialInterval.isZero()) {
          throw new IllegalArgumentException("Invalid interval: " + initialInterval);
        }
        this.initialInterval = initialInterval;
      } else {
        this.initialInterval = null;
      }
      return this;
    }

    /**
     * Interval of the first retry, on congestion related failures (i.e. RESOURCE_EXHAUSTED errors).
     * If coefficient is 1.0 then it is used for all retries. Defaults to 1000ms.
     *
     * @param congestionInitialInterval Interval to wait on first retry, on congestion failures.
     *     Defaults to 1000ms, which is used if set to {@code null}.
     */
    public Builder setCongestionInitialInterval(Duration congestionInitialInterval) {
      if (initialInterval != null) {
        if (congestionInitialInterval.isNegative() || congestionInitialInterval.isZero()) {
          throw new IllegalArgumentException("Invalid interval: " + congestionInitialInterval);
        }
        this.congestionInitialInterval = congestionInitialInterval;
      } else {
        this.congestionInitialInterval = null;
      }
      return this;
    }

    /**
     * Maximum time to retry. When exceeded the retries stop even if maximum retries is not reached
     * yet. Defaults to 1 minute.
     *
     * <p>At least one of expiration or {@link #setMaximumAttempts(int)} is required to be set.
     *
     * @param expiration Maximum time to retry. Defaults to 1 minute, which is used if set to {@code
     *     null}.
     */
    public Builder setExpiration(Duration expiration) {
      if (expiration != null) {
        if (expiration.isNegative() || expiration.isZero()) {
          throw new IllegalArgumentException("Invalid interval: " + expiration);
        }
        this.expiration = expiration;
      } else {
        this.expiration = null;
      }
      return this;
    }

    /**
     * Coefficient used to calculate the next retry interval. The next retry interval is previous
     * interval multiplied by this coefficient. Must be 1 or larger. Default is 2.0.
     *
     * @param backoffCoefficient Coefficient used to calculate the next retry interval. Defaults to
     *     2.0, which is used if set to {@code 0}.
     */
    public Builder setBackoffCoefficient(double backoffCoefficient) {
      if (backoffCoefficient != 0.0) {
        if (!Double.isFinite(backoffCoefficient) || (backoffCoefficient < 1.0)) {
          throw new IllegalArgumentException("coefficient has to be >= 1.0: " + backoffCoefficient);
        }
        this.backoffCoefficient = backoffCoefficient;
      } else {
        this.backoffCoefficient = 0.0;
      }
      return this;
    }

    /**
     * When exceeded the amount of attempts, stop. Even if expiration time is not reached. <br>
     * Default is unlimited.
     *
     * <p>At least one of maximum attempts or {@link #setExpiration(Duration)} is required to be
     * set.
     *
     * @param maximumAttempts Maximum number of attempts. Defaults to unlimited, which is used if
     *     set to {@code 0}.
     */
    public Builder setMaximumAttempts(int maximumAttempts) {
      if (maximumAttempts < 0) {
        throw new IllegalArgumentException("Invalid maximumAttempts: " + maximumAttempts);
      }
      this.maximumAttempts = maximumAttempts;
      return this;
    }

    /**
     * Maximum interval between retries. Exponential backoff leads to interval increase. This value
     * is the cap of the increase. <br>
     * Default is 100x of initial interval. Can't be less than {@link #setInitialInterval(Duration)}
     *
     * @param maximumInterval the maximum interval value. Defaults to 100x initial interval, which
     *     is used if set to {@code null}.
     */
    public Builder setMaximumInterval(Duration maximumInterval) {
      if (maximumInterval != null) {
        if ((maximumInterval.isNegative() || maximumInterval.isZero())) {
          throw new IllegalArgumentException("Invalid interval: " + maximumInterval);
        }
        this.maximumInterval = maximumInterval;
      } else {
        this.maximumInterval = null;
      }
      return this;
    }

    /**
     * Maximum amount of jitter to apply. 0.2 means that actual retry time can be +/- 20% of the
     * calculated time. Set to 0 to disable jitter. Must be lower than 1. Default is 0.1.
     *
     * @param maximumJitterCoefficient Maximum amount of jitter. Default will be used if set to -1.
     */
    public Builder setMaximumJitterCoefficient(double maximumJitterCoefficient) {
      if (maximumJitterCoefficient != -1.0) {
        if (!Double.isFinite(maximumJitterCoefficient)
            || maximumJitterCoefficient < 0
            || maximumJitterCoefficient >= 1.0) {
          throw new IllegalArgumentException(
              "maximumJitterCoefficient has to be >= 0 and < 1.0: " + maximumJitterCoefficient);
        }
        this.maximumJitterCoefficient = maximumJitterCoefficient;
      } else {
        this.maximumJitterCoefficient = -1.0;
      }
      return this;
    }

    /**
     * Makes request that receives a server response with gRPC {@code code} and failure of {@code
     * detailsClass} type non-retryable.
     *
     * <p>The following gRPC codes are never retried:
     *
     * <ul>
     *   <li>{@link Status.Code#CANCELLED}
     *   <li>{@link Status.Code#INVALID_ARGUMENT}
     *   <li>{@link Status.Code#NOT_FOUND}
     *   <li>{@link Status.Code#ALREADY_EXISTS}
     *   <li>{@link Status.Code#FAILED_PRECONDITION}
     *   <li>{@link Status.Code#PERMISSION_DENIED}
     *   <li>{@link Status.Code#UNAUTHENTICATED}
     *   <li>{@link Status.Code#UNIMPLEMENTED}
     * </ul>
     *
     * @param code gRPC code to don't retry
     * @param detailsClass failure type to don't retry. {@code null} means to wildcard, all failures
     *     with the {@code code} code are non retryable.
     */
    public Builder addDoNotRetry(
        Status.Code code, @Nullable Class<? extends GeneratedMessageV3> detailsClass) {
      doNotRetry.add(new DoNotRetryItem(code, detailsClass));
      return this;
    }

    /**
     * Makes request that receives a server response with gRPC {@code doNotRetryItem.code} and
     * failure of {@code doNotRetryItem.detailsClass} type non-retryable.
     *
     * <p>The following gRPC codes are never retried:
     *
     * <ul>
     *   <li>{@link Status.Code#CANCELLED}
     *   <li>{@link Status.Code#INVALID_ARGUMENT}
     *   <li>{@link Status.Code#NOT_FOUND}
     *   <li>{@link Status.Code#ALREADY_EXISTS}
     *   <li>{@link Status.Code#FAILED_PRECONDITION}
     *   <li>{@link Status.Code#PERMISSION_DENIED}
     *   <li>{@link Status.Code#UNAUTHENTICATED}
     *   <li>{@link Status.Code#UNIMPLEMENTED}
     * </ul>
     *
     * @param doNotRetryItem specifies gRPC code and failure type that shouldn't be retried. If
     *     {@code doNotRetryItem.detailsClass==null}, all failures with the {@code
     *     doNotRetryItem.code} code are non retryable.
     */
    public Builder addDoNotRetry(DoNotRetryItem doNotRetryItem) {
      doNotRetry.add(doNotRetryItem);
      return this;
    }

    /** The parameter options takes precedence. */
    public Builder setRetryOptions(RpcRetryOptions o) {
      if (o == null) {
        return this;
      }
      setInitialInterval(
          OptionsUtils.merge(initialInterval, o.getInitialInterval(), Duration.class));
      setCongestionInitialInterval(
          OptionsUtils.merge(congestionInitialInterval, o.getInitialInterval(), Duration.class));
      setExpiration(OptionsUtils.merge(expiration, o.getExpiration(), Duration.class));
      setMaximumInterval(
          OptionsUtils.merge(maximumInterval, o.getMaximumInterval(), Duration.class));
      setBackoffCoefficient(
          OptionsUtils.merge(backoffCoefficient, o.getBackoffCoefficient(), double.class));
      setMaximumAttempts(OptionsUtils.merge(maximumAttempts, o.getMaximumAttempts(), int.class));
      if (o.getMaximumJitterCoefficient() != -1.0) {
        setMaximumJitterCoefficient(o.getMaximumJitterCoefficient());
      }
      doNotRetry = merge(doNotRetry, o.getDoNotRetry());
      validateBuildWithDefaults();
      return this;
    }

    private List<DoNotRetryItem> merge(List<DoNotRetryItem> o1, List<DoNotRetryItem> o2) {
      if (o2 != null) {
        return new ArrayList<>(o2);
      }
      if (o1.size() > 0) {
        return new ArrayList<>(o1);
      }
      return null;
    }

    /**
     * Build RetryOptions without performing validation as validation should be done after merging
     * with MethodRetry.
     */
    public RpcRetryOptions build() {
      return new RpcRetryOptions(
          initialInterval,
          congestionInitialInterval,
          backoffCoefficient,
          expiration,
          maximumAttempts,
          maximumInterval,
          maximumJitterCoefficient,
          doNotRetry);
    }

    public RpcRetryOptions buildWithDefaultsFrom(RpcRetryOptions rpcRetryOptions) {
      return RpcRetryOptions.newBuilder()
          .setRetryOptions(rpcRetryOptions)
          .validateBuildWithDefaults();
    }

    /** Validates property values and builds RetryOptions with default values. */
    public RpcRetryOptions validateBuildWithDefaults() {
      double backoff = backoffCoefficient;
      if (backoff == 0d) {
        backoff = DefaultStubServiceOperationRpcRetryOptions.BACKOFF;
      }
      if (initialInterval == null || initialInterval.isZero() || initialInterval.isNegative()) {
        initialInterval = DefaultStubServiceOperationRpcRetryOptions.INITIAL_INTERVAL;
      }
      if (congestionInitialInterval == null
          || congestionInitialInterval.isZero()
          || congestionInitialInterval.isNegative()) {
        congestionInitialInterval =
            DefaultStubServiceOperationRpcRetryOptions.CONGESTION_INITIAL_INTERVAL;
      }
      if (expiration == null || expiration.isZero() || expiration.isNegative()) {
        expiration = DefaultStubServiceOperationRpcRetryOptions.EXPIRATION_INTERVAL;
      }

      Duration maxInterval = this.maximumInterval;

      if (maxInterval == null || maxInterval.isZero() || maxInterval.isNegative()) {
        if (maximumAttempts == 0) {
          maxInterval = DefaultStubServiceOperationRpcRetryOptions.MAXIMUM_INTERVAL;
        } else {
          maxInterval = null;
        }
      }

      if (maximumJitterCoefficient == -1.0) {
        maximumJitterCoefficient =
            DefaultStubServiceOperationRpcRetryOptions.MAXIMUM_JITTER_COEFFICIENT;
      }

      RpcRetryOptions result =
          new RpcRetryOptions(
              initialInterval,
              congestionInitialInterval,
              backoff,
              expiration,
              maximumAttempts,
              maxInterval,
              maximumJitterCoefficient,
              MoreObjects.firstNonNull(doNotRetry, Collections.emptyList()));
      result.validate();
      return result;
    }
  }

  private final Duration initialInterval;

  private final Duration congestionInitialInterval;

  private final double backoffCoefficient;

  private final Duration expiration;

  private final int maximumAttempts;

  private final Duration maximumInterval;

  private final double maximumJitterCoefficient;

  private final @Nonnull List<DoNotRetryItem> doNotRetry;

  private RpcRetryOptions(
      Duration initialInterval,
      Duration congestionInitialInterval,
      double backoffCoefficient,
      Duration expiration,
      int maximumAttempts,
      Duration maximumInterval,
      double maximumJitterCoefficient,
      @Nonnull List<DoNotRetryItem> doNotRetry) {
    this.initialInterval = initialInterval;
    this.congestionInitialInterval = congestionInitialInterval;
    this.backoffCoefficient = backoffCoefficient;
    this.expiration = expiration;
    this.maximumAttempts = maximumAttempts;
    this.maximumInterval = maximumInterval;
    this.maximumJitterCoefficient = maximumJitterCoefficient;
    this.doNotRetry = Collections.unmodifiableList(doNotRetry);
  }

  public Duration getInitialInterval() {
    return initialInterval;
  }

  public Duration getCongestionInitialInterval() {
    return congestionInitialInterval;
  }

  public double getBackoffCoefficient() {
    return backoffCoefficient;
  }

  public Duration getExpiration() {
    return expiration;
  }

  public int getMaximumAttempts() {
    return maximumAttempts;
  }

  public Duration getMaximumInterval() {
    return maximumInterval;
  }

  public double getMaximumJitterCoefficient() {
    return maximumJitterCoefficient;
  }

  public void validate() {
    validate(true);
  }

  public void validate(boolean hasToBeFinite) {
    if (initialInterval == null) {
      throw new IllegalStateException("required property initialInterval not set");
    }
    if (congestionInitialInterval == null) {
      throw new IllegalStateException("required property congestionInitialInterval not set");
    }
    if (maximumAttempts < 0) {
      throw new IllegalArgumentException("negative maximum attempts");
    }
    if (hasToBeFinite && expiration == null && maximumAttempts == 0) {
      throw new IllegalArgumentException(
          "both MaximumAttempts and Expiration on retry policy are not set, at least one of them must be set");
    }
    if (maximumInterval != null && maximumInterval.compareTo(initialInterval) < 0) {
      throw new IllegalStateException(
          "maximumInterval("
              + maximumInterval
              + ") cannot be smaller than initialInterval("
              + initialInterval);
    }
    if (backoffCoefficient != 0d && backoffCoefficient < 1.0) {
      throw new IllegalArgumentException("coefficient less than 1");
    }
    if (!Double.isFinite(maximumJitterCoefficient)
        || maximumJitterCoefficient < 0
        || maximumJitterCoefficient >= 1.0) {
      throw new IllegalArgumentException(
          "maximumJitterCoefficient has to be >= 0 and < 1.0: " + maximumJitterCoefficient);
    }
  }

  public @Nonnull List<DoNotRetryItem> getDoNotRetry() {
    return doNotRetry;
  }

  @Override
  public String toString() {
    return "RetryOptions{"
        + "initialInterval="
        + initialInterval
        + "congestionInitialInterval="
        + congestionInitialInterval
        + ", backoffCoefficient="
        + backoffCoefficient
        + ", expiration="
        + expiration
        + ", maximumAttempts="
        + maximumAttempts
        + ", maximumInterval="
        + maximumInterval
        + ", maximumJitterCoefficient="
        + maximumJitterCoefficient
        + ", doNotRetry="
        + doNotRetry
        + '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    RpcRetryOptions that = (RpcRetryOptions) o;
    return Double.compare(that.backoffCoefficient, backoffCoefficient) == 0
        && maximumAttempts == that.maximumAttempts
        && Objects.equals(initialInterval, that.initialInterval)
        && Objects.equals(congestionInitialInterval, that.congestionInitialInterval)
        && Objects.equals(expiration, that.expiration)
        && Objects.equals(maximumInterval, that.maximumInterval)
        && Objects.equals(maximumJitterCoefficient, that.maximumJitterCoefficient)
        && Objects.equals(doNotRetry, that.doNotRetry);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        initialInterval,
        congestionInitialInterval,
        backoffCoefficient,
        expiration,
        maximumAttempts,
        maximumInterval,
        maximumJitterCoefficient,
        doNotRetry);
  }
}
