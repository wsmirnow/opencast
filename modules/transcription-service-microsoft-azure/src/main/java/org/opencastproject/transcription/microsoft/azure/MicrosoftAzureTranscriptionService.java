/**
 * Licensed to The Apereo Foundation under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 *
 * The Apereo Foundation licenses this file to you under the Educational
 * Community License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License
 * at:
 *
 *   http://opensource.org/licenses/ecl2.txt
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 */
package org.opencastproject.transcription.microsoft.azure;

import org.opencastproject.assetmanager.api.AssetManager;
import org.opencastproject.job.api.AbstractJobProducer;
import org.opencastproject.job.api.Job;
import org.opencastproject.mediapackage.MediaPackageElement;
import org.opencastproject.mediapackage.MediaPackageElementParser;
import org.opencastproject.mediapackage.MediaPackageException;
import org.opencastproject.mediapackage.Track;
import org.opencastproject.security.api.OrganizationDirectoryService;
import org.opencastproject.security.api.SecurityService;
import org.opencastproject.security.api.UserDirectoryService;
import org.opencastproject.serviceregistry.api.ServiceRegistry;
import org.opencastproject.serviceregistry.api.ServiceRegistryException;
import org.opencastproject.transcription.api.TranscriptionService;
import org.opencastproject.transcription.api.TranscriptionServiceException;
import org.opencastproject.transcription.persistence.TranscriptionDatabase;
import org.opencastproject.util.NotFoundException;
import org.opencastproject.util.OsgiUtil;
import org.opencastproject.util.data.Option;
import org.opencastproject.workflow.api.WorkflowService;
import org.opencastproject.workingfilerepository.api.WorkingFileRepository;
import org.opencastproject.workspace.api.Workspace;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

@Component(immediate = true, service = { TranscriptionService.class,
    MicrosoftAzureTranscriptionService.class }, property = {
    "service.description=Microsoft Azure Transcription Service", "provider=microsoft.azure" })
public class MicrosoftAzureTranscriptionService extends AbstractJobProducer implements TranscriptionService {

  private static final Logger logger = LoggerFactory.getLogger(MicrosoftAzureTranscriptionService.class);

  private static final String JOB_TYPE = "org.opencastproject.transcription.microsoft.azure";

  private static final String DEFAULT_LANGUAGE = "en-GB";
  private static final String DEFAULT_AZURE_BLOB_PATH = "";
  private static final String DEFAULT_AZURE_CONTAINER_NAME = "opencast-transcriptions";
  private static final String KEY_ENABLED = "enabled";
  private static final String KEY_LANGUAGE = "language";
  private static final String KEY_AZURE_STORAGE_ACCOUNT_NAME = "azure_storage_account_name";
  private static final String KEY_AZURE_ACCOUNT_ACCESS_KEY = "azure_account_access_key";
  private static final String KEY_AZURE_BOLB_PATH = "azure_blob_path";
  private static final String KEY_AZURE_CONTAINER_NAME = "azure_container_name";

  private AssetManager assetManager;
  private OrganizationDirectoryService organizationDirectoryService;
  private ScheduledExecutorService scheduledExecutor;
  private SecurityService securityService;
  private ServiceRegistry serviceRegistry;
  private TranscriptionDatabase database;
  private UserDirectoryService userDirectoryService;
  private WorkflowService workflowService;
  private WorkingFileRepository wfr;
  private Workspace workspace;
  private boolean enabled;
  private String language;
  private String azureStorageAccountName;
  private String azureAccountAccessKey;
  private String azureBlobPath;
  private String azureContainerName;

  private enum Operation {
    StartTranscription
  }

  /**
   * A public constructor, required by OSGi.
   */
  public MicrosoftAzureTranscriptionService() {
    super(JOB_TYPE);
  }

  @Activate
  @Modified
  public void activate(ComponentContext cc) {
    super.activate(cc);
    Option<Boolean> enabledOpt = OsgiUtil.getOptCfgAsBoolean(cc.getProperties(), KEY_ENABLED);
    if (enabledOpt.isSome()) {
      enabled = enabledOpt.get();
    } else {
      enabled = false;
    }

    if (!enabled) {
      logger.info("Microsoft Azure Transcription service disabled."
          + " If you want to enable it, please update the service configuration.");
      return;
    }

    Option<String> azureStorageAccountNameKeyOpt = OsgiUtil.getOptCfg(cc.getProperties(),
        KEY_AZURE_STORAGE_ACCOUNT_NAME);
    if (azureStorageAccountNameKeyOpt.isSome()) {
      azureStorageAccountName = azureStorageAccountNameKeyOpt.get();
    } else {
      logger.warn("Azure storage account name key was not set. Disabling Microsoft Azure transcription service.");
      enabled = false;
      return;
    }

    Option<String> azureAccountAccessKeyKeyOpt = OsgiUtil.getOptCfg(cc.getProperties(), KEY_AZURE_ACCOUNT_ACCESS_KEY);
    if (azureAccountAccessKeyKeyOpt.isSome()) {
      azureAccountAccessKey = azureAccountAccessKeyKeyOpt.get();
    } else {
      logger.warn("Azure storage account access key was not set. Disabling Microsoft Azure transcription service.");
      enabled = false;
      return;
    }

    Option<String> languageOpt = OsgiUtil.getOptCfg(cc.getProperties(), KEY_LANGUAGE);
    if (languageOpt.isSome()) {
      language = languageOpt.get();
      logger.info("Default Language is set to '{}'.", language);
    } else {
      language = DEFAULT_LANGUAGE;
      logger.info("Default language '{}' will be used.", language);
    }

    Option<String> azureContainerNameKeyOpt = OsgiUtil.getOptCfg(cc.getProperties(), KEY_AZURE_CONTAINER_NAME);
    if (azureContainerNameKeyOpt.isSome()) {
      azureContainerName = azureContainerNameKeyOpt.get();
    } else {
      logger.debug("Azure storage container name was not set, using default path.");
      azureContainerName = DEFAULT_AZURE_CONTAINER_NAME;
    }

    Option<String> azureBlobPathKeyOpt = OsgiUtil.getOptCfg(cc.getProperties(), KEY_AZURE_BOLB_PATH);
    if (azureBlobPathKeyOpt.isSome()) {
      azureBlobPath = azureBlobPathKeyOpt.get();
    } else {
      logger.debug("Azure blob path was not set, using default path.");
      azureBlobPath = DEFAULT_AZURE_BLOB_PATH;
    }
    logger.info("Activated.");
  }

  @Deactivate
  public void deactivate(ComponentContext cc) {

  }

  @Override
  protected String process(Job job) throws Exception {
    Operation op = null;
    String operation = job.getOperation();
    List<String> arguments = job.getArguments();
    op = Operation.valueOf(operation);
    switch (op) {
      case StartTranscription:
        String mpId = arguments.get(0);
        Track track = (Track) MediaPackageElementParser.getFromXml(arguments.get(1));
        String languageCode = arguments.get(2);
        return createTranscriptionJob(mpId, track, languageCode);
      default:
        throw new IllegalStateException("Don't know how to handle operation '" + operation + "'");
    }
  }

  @Override
  public Job startTranscription(String mpId, Track track) throws TranscriptionServiceException {
    return startTranscription(mpId, track, getLanguage());
  }

  @Override
  public Job startTranscription(String mpId, Track track, String... args) throws TranscriptionServiceException {
    try {
      List<String> jobArgs = new ArrayList<>(2 + args.length);
      jobArgs.add(mpId);
      jobArgs.add(MediaPackageElementParser.getAsXml(track));
      jobArgs.addAll(Arrays.asList(args));
      return serviceRegistry.createJob(JOB_TYPE, Operation.StartTranscription.toString(),jobArgs);
    } catch (ServiceRegistryException e) {
      throw new TranscriptionServiceException(String.format(
          "Unable to create transcription job for media package '%s'.", mpId), e);
    } catch (MediaPackageException e) {
      throw new TranscriptionServiceException(String.format(
          "Unable to to parse track from media package '%s'.", mpId), e);
    }
  }

  @Override
  public MediaPackageElement getGeneratedTranscription(String mpId, String jobId) throws TranscriptionServiceException {
    throw new TranscriptionServiceException("Not implemented.");
  }

  @Override
  public void transcriptionDone(String mpId, Object results) throws TranscriptionServiceException {

  }

  @Override
  public void transcriptionError(String mpId, Object results) throws TranscriptionServiceException {

  }

  @Override
  public String getLanguage() {
    return language;
  }

  @Override
  public Map<String, Object> getReturnValues(String mpId, String jobId) throws TranscriptionServiceException {
    return null;
  }

  public String createTranscriptionJob(String mpId, Track track, String language) throws TranscriptionServiceException {
    // load media file into workspace
    File trackFile;
    try {
      trackFile = workspace.get(track.getURI());
    } catch (NotFoundException e) {
      throw new TranscriptionServiceException(String.format("Track %s not found.", track.getURI()), e);
    } catch (IOException e) {
      throw new TranscriptionServiceException(String.format(
          "Unable to get track %s for transcription.", track.getURI()), e);
    }
    // upload media file to azure storage
    //// create Azure storage client
    MicrosoftAzureStorageClient azureStorageClient;
    try {
      azureStorageClient = new MicrosoftAzureStorageClient(azureStorageAccountName,
          azureAccountAccessKey);
    } catch (MicrosoftAzureStorageClientException e) {
      throw new TranscriptionServiceException("Unable to create Microsoft Azure storage client.", e);
    }
    //// assure azure storage container exists
    try {
      azureStorageClient.createContainer(mpId, azureContainerName);
    } catch (IOException | MicrosoftAzureStorageClientException | MicrosoftAzureNotAllowedException e) {
      throw new TranscriptionServiceException(String.format(
          "Unable to query or create a storage container '%s' on Microsoft Azure.", azureContainerName), e);
    }
    //// upload file to azure storage container
    try {
      String foo = azureStorageClient.uploadFile(mpId, trackFile, azureContainerName, azureBlobPath);
    } catch (IOException | MicrosoftAzureStorageClientException e) {
      throw new TranscriptionServiceException(String.format(
          "Unable to upload track '%s' from media package '%s' to Microsoft Azure storage container '%s'.",
          track.getURI(), mpId, azureContainerName), e);
    }
    // start azure transcription job
    // store transcription job ID and status
    // return transcription job ID
    return "";
  }

  @Override
  protected ServiceRegistry getServiceRegistry() {
    return serviceRegistry;
  }

  @Override
  protected SecurityService getSecurityService() {
    return securityService;
  }

  @Override
  protected UserDirectoryService getUserDirectoryService() {
    return userDirectoryService;
  }

  @Override
  protected OrganizationDirectoryService getOrganizationDirectoryService() {
    return organizationDirectoryService;
  }

  @Reference
  public void setServiceRegistry(ServiceRegistry serviceRegistry) {
    this.serviceRegistry = serviceRegistry;
  }

  @Reference
  public void setSecurityService(SecurityService securityService) {
    this.securityService = securityService;
  }

  @Reference
  public void setUserDirectoryService(UserDirectoryService userDirectoryService) {
    this.userDirectoryService = userDirectoryService;
  }

  @Reference
  public void setOrganizationDirectoryService(OrganizationDirectoryService organizationDirectoryService) {
    this.organizationDirectoryService = organizationDirectoryService;
  }

  @Reference
  public void setWorkspace(Workspace ws) {
    this.workspace = ws;
  }

  @Reference
  public void setWorkingFileRepository(WorkingFileRepository wfr) {
    this.wfr = wfr;
  }

  @Reference
  public void setDatabase(TranscriptionDatabase service) {
    this.database = service;
  }

  @Reference
  public void setAssetManager(AssetManager service) {
    this.assetManager = service;
  }

  @Reference
  public void setWorkflowService(WorkflowService service) {
    this.workflowService = service;
  }
}
