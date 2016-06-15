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

import _ss_com.streamsets.pipeline.lib.util.ThreadUtil;
import com.sforce.async.*;
import com.sforce.soap.partner.Connector;
import com.sforce.soap.partner.PartnerConnection;
import com.sforce.ws.ConnectionException;
import com.sforce.ws.ConnectorConfig;
import com.sforce.ws.SessionRenewer;
import com.streamsets.forcelib.stage.lib.sample.Errors;
import com.streamsets.pipeline.api.*;
import com.streamsets.pipeline.api.base.BaseSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.namespace.QName;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This source is an example and does not actually read from anywhere.
 * It does however, generate generate a simple record with one field.
 */
public abstract class ForceBulkSource extends BaseSource {
  private static final Logger LOG = LoggerFactory.getLogger(ForceBulkSource.class);
  private static final String JOBID_PREFIX = "750";
  private static final String RESULTID_PREFIX = "752";

  /**
   * Gives access to the UI configuration of the stage provided by the {@link ForceBulkDSource} class.
   */
  public abstract String getUsername();
  public abstract String getPassword();
  public abstract String getAuthEndpoint();
  public abstract String getApiVersion();
  public abstract String getSoqlQuery();
  public abstract String getOffsetColumn();
  public abstract String getInitialOffset();
  public abstract long getQueryInterval();
  public abstract boolean getIncrementalMode();

  private BulkConnection connection;
  private String sobjectType;
  private long sleepTime = 1000L;
  private BatchInfo batch;
  private JobInfo job;
  private QueryResultList queryResultList;
  private int resultIndex;
  private CSVReader rdr;
  private List<String> resultHeader;
  private long lastQueryCompletedTime = 0L;

  // Renew the Salesforce session on timeout
  public class ForceSessionRenewer implements SessionRenewer {
    @Override
    public SessionRenewalHeader renewSession(ConnectorConfig config) throws ConnectionException {
      PartnerConnection connection = Connector.newConnection(getPartnerConfig());

      SessionRenewalHeader header = new SessionRenewalHeader();
      header.name = new QName("urn:enterprise.soap.sforce.com", "SessionHeader");
      header.headerElement = connection.getSessionHeader();
      return header;
    }
  }

  protected ConnectorConfig getPartnerConfig() {
    ConnectorConfig partnerConfig = new ConnectorConfig();

    partnerConfig.setUsername(getUsername());
    partnerConfig.setPassword(getPassword());
    partnerConfig.setAuthEndpoint("https://"+getAuthEndpoint()+"/services/Soap/u/"+getApiVersion());
    partnerConfig.setCompression(true);
    partnerConfig.setSessionRenewer(new ForceSessionRenewer());

    return partnerConfig;
  }

  protected BulkConnection getBulkConnection(ConnectorConfig partnerConfig) throws ConnectionException, AsyncApiException {
    // When PartnerConnection is instantiated, a login is implicitly
    // executed and, if successful,
    // a valid session is stored in the ConnectorConfig instance.
    // Use this key to initialize a BulkConnection:
    ConnectorConfig config = new ConnectorConfig();
    config.setSessionId(partnerConfig.getSessionId());

    // The endpoint for the Bulk API service is the same as for the normal
    // SOAP uri until the /Soap/ part. From here it's '/async/versionNumber'
    String soapEndpoint = partnerConfig.getServiceEndpoint();
    String restEndpoint = soapEndpoint.substring(0, soapEndpoint.indexOf("Soap/"))
            + "async/" + getApiVersion();
    config.setRestEndpoint(restEndpoint);
    // This should only be false when doing debugging.
    config.setCompression(true);
    // Set this to true to see HTTP requests and responses on stdout
    config.setTraceMessage(false);
    BulkConnection connection = new BulkConnection(config);
    return connection;
  }

  protected BulkConnection login() throws ConnectionException, AsyncApiException {
    ConnectorConfig partnerConfig = getPartnerConfig();

    // Creating the connection automatically handles login and stores
    // the session in partnerConfig
    new PartnerConnection(partnerConfig);

    return getBulkConnection(partnerConfig);
  }

  @Override
  protected List<ConfigIssue> init() {
    // Validate configuration values and open any required resources.
    List<ConfigIssue> issues = super.init();

    final String formattedOffsetColumn = Pattern.quote(getOffsetColumn().toUpperCase());
    Pattern offsetColumnInWhereAndOrderByClause = Pattern.compile(
            String.format("(?s).*\\bWHERE\\b.*(\\b%s\\b).*\\bORDER BY\\b.*\\b%s\\b.*",
                    formattedOffsetColumn,
                    formattedOffsetColumn
            )
    );

    if (!offsetColumnInWhereAndOrderByClause.matcher(getSoqlQuery().toUpperCase()).matches()) {
      issues.add(getContext().createConfigIssue(Groups.FORCE.name(), "connectorConfig", Errors.FORCE_07, getOffsetColumn()));
    }

    try {
      connection = login();

      LOG.info("Successfully authenticated as {}", getUsername());

      String soqlQuery = getSoqlQuery();
      Pattern p = Pattern.compile("^SELECT.*FROM\\s*(\\S*)\\s.*", Pattern.DOTALL);
      Matcher m = p.matcher(soqlQuery);
      if (m.matches()) {
        sobjectType = m.group(1);

        LOG.info("Found sobject type {}", sobjectType);
      } else {
        issues.add(
                getContext().createConfigIssue(
                        Groups.FORCE.name(), "connectorConfig", Errors.FORCE_00, "Badly formed SOQL Query: " + soqlQuery
                )
        );
      }
    } catch (ConnectionException | AsyncApiException e) {
      LOG.error("Error connecting: {}", e);
      issues.add(
              getContext().createConfigIssue(
                      Groups.FORCE.name(), "connectorConfig", Errors.FORCE_00, e.getMessage()
              )
      );
    }

    // If issues is not empty, the UI will inform the user of each configuration issue in the list.
    return issues;
  }

  /** {@inheritDoc} */
  @Override
  public void destroy() {
    if (job != null) {
      try {
        connection.abortJob(job.getId());
      } catch (AsyncApiException e) {
        e.printStackTrace();
      }
    }

    job = null;
    batch = null;

    // Clean up any open resources.
    super.destroy();
  }

  private String prepareQuery(String query, String lastSourceOffset) {
    final String offset = (null == lastSourceOffset) ? getInitialOffset() : lastSourceOffset;
    return query.replaceAll("\\$\\{offset\\}", offset);
  }

  /** {@inheritDoc} */
  @Override
  public String produce(String lastSourceOffset, int maxBatchSize, BatchMaker batchMaker) throws StageException {
    String nextSourceOffset = (null == lastSourceOffset) ? getInitialOffset() : lastSourceOffset;

    if (job == null) {
      long now = System.currentTimeMillis();
      long delay = Math.max(0, (lastQueryCompletedTime + (1000 * getQueryInterval())) - now);

      if (delay > 0) {
        // Sleep in one second increments so we don't tie up the app.
        LOG.info("{}ms remaining until next fetch.", delay);
        ThreadUtil.sleep(Math.min(delay, 1000));
      } else {
        // No job in progress - start from scratch
        try {
          job = createJob(sobjectType, connection);
          LOG.info("Created Bulk API job {}", job.getId());
          final String preparedQuery = prepareQuery(getSoqlQuery(), lastSourceOffset);
          LOG.info("SOQL Query is: {}", preparedQuery);
          batch = connection.createBatchFromStream(job,
                  new ByteArrayInputStream(preparedQuery.getBytes(StandardCharsets.UTF_8)));
          LOG.info("Created Bulk API batch {}", batch.getId());
          sleepTime = 1000L;
        } catch (AsyncApiException e) {
          throw new StageException(Errors.FORCE_01, e);
        }
      }
    }

    // We started the job already, see if the results are ready
    if (queryResultList == null && job != null) {
      // Poll for results
      try {
        LOG.info("Waiting {} milliseconds for batch {}", sleepTime, batch.getId());
        Thread.sleep(sleepTime);
      } catch (InterruptedException e) {}
      //sleepTime *= 2;
      try {
        BatchInfo info = connection.getBatchInfo(job.getId(), batch.getId());
        if (info.getState() == BatchStateEnum.Completed) {
          LOG.info("Batch {} completed", batch.getId());
          queryResultList = connection.getQueryResultList(job.getId(), batch.getId());
          LOG.info("Query results: {}", queryResultList.getResult());
          resultIndex = 0;
        } else if (info.getState() == BatchStateEnum.Failed) {
          LOG.info("Batch {} failed: {}", batch.getId(), info.getStateMessage());
          throw new StageException(Errors.FORCE_03, info.getStateMessage());
        } else {
          LOG.info("Batch {} in progress", batch.getId());
          return nextSourceOffset;
        }
      } catch (AsyncApiException e) {
        throw new StageException(Errors.FORCE_02, e);
      }

    }

    if (rdr == null && queryResultList != null) {
      // We have results - retrieve the next one!
      String resultId = queryResultList.getResult()[resultIndex];
      resultIndex++;

      try {
        rdr = new CSVReader(connection.getQueryResultStream(job.getId(), batch.getId(), resultId));
        rdr.setMaxRowsInFile(Integer.MAX_VALUE);
        rdr.setMaxCharsInFile(Integer.MAX_VALUE);
      } catch (AsyncApiException e) {
        throw new StageException(Errors.FORCE_05, e);
      }

      try {
        resultHeader = rdr.nextRecord();
        LOG.info("Result {} header: {}", resultId, resultHeader);
      } catch (IOException e) {
        throw new StageException(Errors.FORCE_04, e);
      }
    }

    if (rdr != null){
      int idIndex = -1;
      if (resultHeader != null &&
              (resultHeader.size() > 1 || !("Records not found for this query".equals(resultHeader.get(0))))) {
        for (int i = 0; i < resultHeader.size(); i++) {
          if (resultHeader.get(i).equalsIgnoreCase(getOffsetColumn())) {
            idIndex = i;
            break;
          }
        }
        if (idIndex == -1) {
          throw new StageException(Errors.FORCE_06, resultHeader);
        }
      }

      int numRecords = 0;
      List<String> row;
      while (numRecords < maxBatchSize) {
        try {
          if ((row = rdr.nextRecord()) == null) {
            // Exhausted this result - come back in on the next batch;
            rdr = null;
            if (resultIndex == queryResultList.getResult().length) {
              // We're out of results, too!
              try {
                connection.closeJob(job.getId());
                lastQueryCompletedTime = System.currentTimeMillis();
                LOG.info("Query completed at: {}", lastQueryCompletedTime);
              } catch (AsyncApiException e) {
                LOG.error("Error closing job: {}", e);
              }
              LOG.info("Partial batch of {} records", numRecords);
              queryResultList = null;
              batch = null;
              job = null;
            }
            return nextSourceOffset;
          } else {
            String id = row.get(idIndex);
            nextSourceOffset = (getIncrementalMode()) ? id : getInitialOffset();
            Record record = getContext().createRecord(id);
            Map<String, Field> map = new HashMap<>();
            for (int i = 0; i < resultHeader.size(); i++) {
              map.put(resultHeader.get(i), Field.create(row.get(i)));
            }
            record.set(Field.create(map));
            batchMaker.addRecord(record);
            ++numRecords;
          }
        } catch (IOException e) {
          throw new StageException(Errors.FORCE_04, e);
        }
      }
      LOG.info("Full batch of {} records", numRecords);

      return nextSourceOffset;
    }

    return nextSourceOffset;
  }

  /**
   * Create a new job using the Bulk API.
   *
   * @param sobjectType
   *            The object type being loaded, such as "Account"
   * @param connection
   *            BulkConnection used to create the new job.
   * @return The JobInfo for the new job.
   * @throws AsyncApiException
   */
  private JobInfo createJob(String sobjectType, BulkConnection connection)
          throws AsyncApiException {
    JobInfo job = new JobInfo();
    job.setObject(sobjectType);
    job.setOperation(OperationEnum.query);
    job.setContentType(ContentType.CSV);
    job = connection.createJob(job);
    return job;
  }
}
