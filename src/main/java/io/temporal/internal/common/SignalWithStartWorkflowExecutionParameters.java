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

package io.temporal.internal.common;

import io.temporal.api.common.v1.Payloads;
import io.temporal.api.workflowservice.v1.StartWorkflowExecutionRequest;
import java.util.Optional;

public class SignalWithStartWorkflowExecutionParameters {

  private final StartWorkflowExecutionRequest startParameters;
  private final String signalName;
  private final Optional<Payloads> signalInput;

  public SignalWithStartWorkflowExecutionParameters(
      StartWorkflowExecutionRequest startParameters,
      String signalName,
      Optional<Payloads> signalInput) {
    this.startParameters = startParameters;
    this.signalName = signalName;
    this.signalInput = signalInput;
  }

  public StartWorkflowExecutionRequest getStartParameters() {
    return startParameters;
  }

  public String getSignalName() {
    return signalName;
  }

  public Optional<Payloads> getSignalInput() {
    return signalInput;
  }
}
