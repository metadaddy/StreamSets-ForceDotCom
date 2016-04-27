/**
 * Copyright 2015 StreamSets Inc.
 *
 * Licensed under the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.streamsets.forcelib.stage.origin.sample;

import _ss_com.streamsets.pipeline.lib.el.TimeEL;
import com.streamsets.pipeline.api.ConfigDef;
import com.streamsets.pipeline.api.ConfigGroups;
import com.streamsets.pipeline.api.ExecutionMode;
import com.streamsets.pipeline.api.GenerateResourceBundle;
import com.streamsets.pipeline.api.StageDef;

@StageDef(
    version = 1,
    label = "Force.com Origin",
    description = "",
    icon = "force.png",
    execution = ExecutionMode.STANDALONE,
    recordsByRef = true,
    resetOffset = true,
    onlineHelpRefUrl = ""
)
@ConfigGroups(value = Groups.class)
@GenerateResourceBundle
public class ForceBulkDSource extends ForceBulkSource {

  @ConfigDef(
          required = true,
          type = ConfigDef.Type.STRING,
          defaultValue = "user@example.com",
          label = "Username",
          description = "Salesforce username",
          displayPosition = 10,
          group = "FORCE"
  )
  public String username;

  @ConfigDef(
          required = true,
          type = ConfigDef.Type.STRING,
          defaultValue = "${runtime:loadResource('forcePassword.txt',true)}",
          label = "Password",
          description = "Salesforce password",
          displayPosition = 20,
          group = "FORCE"
  )
  public String password;

  @ConfigDef(
          required = true,
          type = ConfigDef.Type.STRING,
          defaultValue = "login.salesforce.com",
          label = "Auth Endpoint",
          description = "Salesforce SOAP API Authentication Endpoint: login.salesforce.com for production/Developer Edition, test.salesforce.com for sandboxes",
          displayPosition = 30,
          group = "FORCE"
  )
  public String authEndpoint;

  @ConfigDef(
          required = true,
          type = ConfigDef.Type.STRING,
          defaultValue = "36.0",
          label = "API Version",
          description = "Salesforce SOAP API Version",
          displayPosition = 40,
          group = "FORCE"
  )
  public String apiVersion;

  @ConfigDef(
          required = true,
          type = ConfigDef.Type.BOOLEAN,
          defaultValue = "true",
          label = "Incremental Mode",
          description = "Disabling Incremental Mode will always substitute the value in" +
                  " Initial Offset in place of ${OFFSET} instead of the most recent value of <offsetColumn>.",
          displayPosition = 45,
          group = "FORCE"
  )
  public boolean isIncrementalMode;

  @ConfigDef(
          required = true,
          type = ConfigDef.Type.TEXT,
          mode = ConfigDef.Mode.SQL,
          defaultValue = "SELECT Id, Name FROM Account WHERE Id > '${OFFSET}' ORDER BY Id",
          label = "SOQL Query",
          description =
                  "SELECT <offset field>, ... FROM <object name> WHERE <offset field>  >  ${OFFSET} ORDER BY <offset field>",
          elDefs = {OffsetEL.class},
          evaluation = ConfigDef.Evaluation.IMPLICIT,
          displayPosition = 50,
          group = "FORCE"
  )
  public String soqlQuery;

  @ConfigDef(
          required = true,
          type = ConfigDef.Type.TEXT,
          defaultValue = "000000000000000",
          label = "Initial Offset",
          description = "Initial value to insert for ${offset}." +
                  " Subsequent queries will use the result of the Next Offset Query",
          displayPosition = 70,
          group = "FORCE"
  )
  public String initialOffset;

  @ConfigDef(
          required = true,
          type = ConfigDef.Type.TEXT,
          defaultValue = "Id",
          label = "Offset Field",
          description = "Field checked to track current offset.",
          displayPosition = 60,
          group = "FORCE"
  )
  public String offsetColumn;

  @ConfigDef(
          required = true,
          type = ConfigDef.Type.NUMBER,
          defaultValue = "${1 * MINUTES}",
          label = "Query Interval",
          displayPosition = 80,
          elDefs = {TimeEL.class},
          evaluation = ConfigDef.Evaluation.IMPLICIT,
          group = "FORCE"
  )
  public long queryInterval;

  /** {@inheritDoc} */
  @Override
  public String getUsername() {
    return username;
  }

  /** {@inheritDoc} */
  @Override
  public String getPassword() {
    return password;
  }

  /** {@inheritDoc} */
  @Override
  public String getAuthEndpoint() {
    return authEndpoint;
  }

  /** {@inheritDoc} */
  @Override
  public String getApiVersion() {
    return apiVersion;
  }

  /** {@inheritDoc} */
  @Override
  public boolean getIncrementalMode() { return isIncrementalMode; }

  /** {@inheritDoc} */
  @Override
  public String getSoqlQuery() {
    return soqlQuery;
  }

  /** {@inheritDoc} */
  @Override
  public String getOffsetColumn() {
    return offsetColumn;
  }

  /** {@inheritDoc} */
  public String getInitialOffset() {
    return initialOffset;
  }

  /** {@inheritDoc} */
  @Override
  public long getQueryInterval() {
    return queryInterval;
  }

}
