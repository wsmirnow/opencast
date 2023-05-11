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
import org.opencastproject.mediapackage.MediaPackageElementBuilderFactory;
import org.opencastproject.mediapackage.MediaPackageElementFlavor;
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
import org.opencastproject.transcription.microsoft.azure.model.MicrosoftAzureSpeechTranscription;
import org.opencastproject.transcription.microsoft.azure.model.MicrosoftAzureSpeechTranscriptionFile;
import org.opencastproject.transcription.microsoft.azure.model.MicrosoftAzureSpeechTranscriptionFiles;
import org.opencastproject.transcription.persistence.TranscriptionDatabase;
import org.opencastproject.transcription.persistence.TranscriptionDatabaseException;
import org.opencastproject.transcription.persistence.TranscriptionJobControl;
import org.opencastproject.util.NotFoundException;
import org.opencastproject.util.OsgiUtil;
import org.opencastproject.util.data.Option;
import org.opencastproject.workflow.api.WorkflowService;
import org.opencastproject.workingfilerepository.api.WorkingFileRepository;
import org.opencastproject.workspace.api.Workspace;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
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
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
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
  private static final float DEFAULT_MIN_CONFIDENCE = 0.7f;
  private static final String KEY_ENABLED = "enabled";
  private static final String KEY_LANGUAGE = "language";
  private static final String KEY_AZURE_STORAGE_ACCOUNT_NAME = "azure_storage_account_name";
  private static final String KEY_AZURE_ACCOUNT_ACCESS_KEY = "azure_account_access_key";
  private static final String KEY_AZURE_BOLB_PATH = "azure_blob_path";
  private static final String KEY_AZURE_CONTAINER_NAME = "azure_container_name";
  private static final String KEY_AZURE_SPEECH_SERVICES_ENDPOINT = "azure_speech_services_endpoint";
  private static final String KEY_COGNITIVE_SERVICES_SUBSCRIPTION_KEY = "azure_cognitive_services_subscription_key";
  private static final String KEY_AZURE_SPEECH_RECOGNITION_MIN_CONFIDENCE = "azure_speech_recognition_min_confidence";


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
  private String azureSpeechServicesEndpoint;
  private String azureCognitiveServicesSubscriptionKey;
  private MicrosoftAzureAuthorization azureAuthorization;
  private MicrosoftAzureStorageClient azureStorageClient;
  private MicrosoftAzureSpeechServicesClient azureSpeechServicesClient;
  private Float azureSpeechRecognitionMinConfidence;

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
      deactivate(cc);
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
      deactivate(cc);
      return;
    }

    Option<String> azureAccountAccessKeyKeyOpt = OsgiUtil.getOptCfg(cc.getProperties(), KEY_AZURE_ACCOUNT_ACCESS_KEY);
    if (azureAccountAccessKeyKeyOpt.isSome()) {
      azureAccountAccessKey = azureAccountAccessKeyKeyOpt.get();
    } else {
      logger.warn("Azure storage account access key was not set. Disabling Microsoft Azure transcription service.");
      deactivate(cc);
      return;
    }

    Option<String> azureSpeechServicesKeyOpt = OsgiUtil.getOptCfg(cc.getProperties(),
        KEY_AZURE_SPEECH_SERVICES_ENDPOINT);
    if (azureSpeechServicesKeyOpt.isSome()) {
      azureSpeechServicesEndpoint = azureSpeechServicesKeyOpt.get();
    } else {
      logger.warn("Azure speech services endpoint was not set. Disabling Microsoft Azure transcription service.");
      deactivate(cc);
      return;
    }

    Option<String> azureCognitiveServicesSubscriptionKeyKeyOpt = OsgiUtil.getOptCfg(cc.getProperties(),
        KEY_COGNITIVE_SERVICES_SUBSCRIPTION_KEY);
    if (azureCognitiveServicesSubscriptionKeyKeyOpt.isSome()) {
      azureCognitiveServicesSubscriptionKey = azureCognitiveServicesSubscriptionKeyKeyOpt.get();
    } else {
      logger.warn("Azure cognitive services subscription key was not set. "
          + "Disabling Microsoft Azure transcription service.");
      deactivate(cc);
      return;
    }

    // optional values
    Option<String> azureBlobPathKeyOpt = OsgiUtil.getOptCfg(cc.getProperties(), KEY_AZURE_BOLB_PATH);
    if (azureBlobPathKeyOpt.isSome()) {
      azureBlobPath = azureBlobPathKeyOpt.get();
    } else {
      logger.debug("Azure blob path was not set, using default path.");
      azureBlobPath = DEFAULT_AZURE_BLOB_PATH;
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

    Option<String> azureSpeechRecognitionMinConfidenceKeyOpt = OsgiUtil.getOptCfg(cc.getProperties(),
        KEY_AZURE_SPEECH_RECOGNITION_MIN_CONFIDENCE);
    if (azureSpeechRecognitionMinConfidenceKeyOpt.isSome()) {
      String azureSpeechRecognitionMinConfidenceStr = azureSpeechRecognitionMinConfidenceKeyOpt.get();
      try {
        azureSpeechRecognitionMinConfidence = Float.valueOf(azureSpeechRecognitionMinConfidenceStr);
      } catch (NumberFormatException e) {
        logger.error("Azure speech recognition min confidence value is not valid. "
            + "Please set a value between 0.0 and 1.0. "
            + "Setting to default value of {}.", DEFAULT_MIN_CONFIDENCE);
        azureSpeechRecognitionMinConfidence = DEFAULT_MIN_CONFIDENCE;
      }
    } else {
      logger.debug("Azure speech recognition min confidence value was not set. Setting to default value of {}.",
          DEFAULT_MIN_CONFIDENCE);
      azureSpeechRecognitionMinConfidence = DEFAULT_MIN_CONFIDENCE;
    }

    //// create Azure storage client
    try {
      azureAuthorization = new MicrosoftAzureAuthorization(azureStorageAccountName, azureAccountAccessKey);
      azureStorageClient = new MicrosoftAzureStorageClient(azureAuthorization);
    } catch (MicrosoftAzureStorageClientException e) {
      logger.error("Unable to create Microsoft Azure storage client. "
          + "Deactivating Microsoft Azure Transcription service.", e);
      deactivate(cc);
      return;
    }

    // create Azure Speech Services client
    azureSpeechServicesClient = new MicrosoftAzureSpeechServicesClient(
        azureSpeechServicesEndpoint, azureCognitiveServicesSubscriptionKey);
    logger.info("Activated.");
  }

  @Deactivate
  public void deactivate(ComponentContext cc) {
    enabled = false;
    azureAuthorization = null;
    azureStorageClient = null;
    azureSpeechServicesClient = null;
  }

  @Override
  protected String process(Job job) throws Exception {
    Operation op = null;
    String operation = job.getOperation();
    List<String> arguments = job.getArguments();
    op = Operation.valueOf(operation);
    switch (op) {
      case StartTranscription:
        long jobId = job.getId();
        String mpId = arguments.get(0);
        Track track = (Track) MediaPackageElementParser.getFromXml(arguments.get(1));
        String languageCode = arguments.get(2);
        return createTranscriptionJob(jobId, mpId, track, languageCode);
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
    MicrosoftAzureSpeechTranscription transcription;
    try {
      transcription = azureSpeechServicesClient.getTranscriptionById(jobId);
    } catch (IOException | MicrosoftAzureNotAllowedException | MicrosoftAzureSpeechClientException e) {
      throw new TranscriptionServiceException(String.format(
          "Unable to get transcription '%s' for media package '%s'.", jobId, mpId), e);
    }
    if (!transcription.isSucceeded()) {
      if (transcription.isRunning()) {
        throw new TranscriptionServiceException(String.format("Unable to get generated transcription. "
            + "Transcription job '%s' for media package '%s' is currently running.", jobId, mpId));
      } else if (transcription.isFailed()) {
        throw new TranscriptionServiceException(String.format("Unable to get generated transcription. "
            + "Transcription job '%s' for media package '%s' is failed.", jobId, mpId));
      }
    }
    // query transcription files
    MicrosoftAzureSpeechTranscriptionFiles transcriptionFiles;
    try {
      transcriptionFiles = azureSpeechServicesClient.getTranscriptionFilesById(
          jobId);
    } catch (IOException | MicrosoftAzureNotAllowedException | MicrosoftAzureSpeechClientException e) {
      throw new TranscriptionServiceException(String.format(
          "Unable to get transcription files '%s' for media package '%s'.", jobId, mpId), e);
    }
    // download transcription file to workspace
    MicrosoftAzureSpeechTranscriptionFile transcriptionFile = null;
    for (MicrosoftAzureSpeechTranscriptionFile tf : transcriptionFiles.values) {
      if (tf.isTranscriptionFile()) {
        transcriptionFile = tf;
        break;
      }
    }
    if (transcriptionFile == null) {
      // get more files with transcriptionFiles.nextLink
      // TODO
      throw new NotImplementedException("At least one transcription file should be provided.");
    }

    URI transcriptionFileUri;
    try {
      transcriptionFileUri = MicrosoftAzureSpeechServicesClient.getTranscriptionFile(transcriptionFile, workspace,
          "webvtt", DEFAULT_MIN_CONFIDENCE);
    } catch (IOException | MicrosoftAzureNotAllowedException | MicrosoftAzureSpeechClientException e) {
      throw new TranscriptionServiceException(String.format(
          "Unable to download transcription file '%s' for media package '%s'.", transcriptionFile.self, mpId), e);
    }
    String lang = Locale.forLanguageTag(transcription.locale).getLanguage();
    String subtype;
    if (StringUtils.isNotBlank(lang)) {
      subtype = "vtt+" + lang;
    } else {
      subtype = "vtt";
    }
    return MediaPackageElementBuilderFactory.newInstance().newElementBuilder()
        .elementFromURI(transcriptionFileUri, MediaPackageElement.Type.Attachment,
            new MediaPackageElementFlavor("captions", subtype));
  }

  @Override
  public void transcriptionDone(String mpId, Object results) throws TranscriptionServiceException {
    MicrosoftAzureSpeechTranscription transcription = (MicrosoftAzureSpeechTranscription) results;
    try {
      database.updateJobControl(transcription.getID(), TranscriptionJobControl.Status.TranscriptionComplete.name());
    } catch (TranscriptionDatabaseException e) {
      throw new TranscriptionServiceException(String.format(
          "Transcription job for media package '%s' succeeded but storing job status in the database failed."
          , mpId), e);
    }
  }

  @Override
  public void transcriptionError(String mpId, Object results) throws TranscriptionServiceException {
    MicrosoftAzureSpeechTranscription transcription = (MicrosoftAzureSpeechTranscription) results;
    try {
      database.updateJobControl(transcription.getID(), TranscriptionJobControl.Status.Error.name());
    } catch (TranscriptionDatabaseException e) {
      throw new TranscriptionServiceException(String.format(
          "Transcription job for media package '%s' failed and storing job status in the database failed too."
          , mpId), e);
    }
  }

  @Override
  public String getLanguage() {
    return language;
  }

  @Override
  public Map<String, Object> getReturnValues(String mpId, String jobId) throws TranscriptionServiceException {
    return null;
  }

  public String createTranscriptionJob(long jobId, String mpId, Track track, String language)
          throws TranscriptionServiceException {
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
    //// assure azure storage container exists
    try {
      azureStorageClient.createContainer(azureContainerName);
    } catch (IOException | MicrosoftAzureStorageClientException | MicrosoftAzureNotAllowedException e) {
      throw new TranscriptionServiceException(String.format(
          "Unable to query or create a storage container '%s' on Microsoft Azure.", azureContainerName), e);
    }
    //// upload file to azure storage container
    String azureBlobUrl;
    try {
      azureBlobUrl = azureStorageClient.uploadFile(trackFile, azureContainerName,
          azureBlobPath, jobId + "." + FilenameUtils.getExtension(trackFile.getName()));
    } catch (IOException | MicrosoftAzureNotAllowedException | MicrosoftAzureStorageClientException e) {
      throw new TranscriptionServiceException(String.format(
          "Unable to upload track %s from media package '%s' to Microsoft Azure storage container '%s'.",
          track.getURI(), mpId, azureContainerName), e);
    }
    // start azure transcription job
    List<String> contentUrls = Arrays.asList(String.format("%s?%s", azureBlobUrl,
        azureAuthorization.generateAccountSASToken("r", "b", null, null, null, null)));
    String azureDestContainerUrl = String.format("%s?%s", azureStorageClient.getContainerUrl(azureContainerName),
        azureAuthorization.generateAccountSASToken("rwl", "b", null, null, null, null));
    MicrosoftAzureSpeechTranscription transcription;
    try {
      transcription = azureSpeechServicesClient.createTranscription(contentUrls,
          azureDestContainerUrl, String.format("Transcription job %d", jobId), language, null, null, null);
      logger.info("Started transcription of {} from media package '{}' on Microsoft Azure Speech Services at {}",
          track.getURI(), mpId, transcription.self);
    } catch (MicrosoftAzureNotAllowedException | IOException | MicrosoftAzureSpeechClientException e) {
      throw new TranscriptionServiceException(String.format(
          "Unable to create transcription of track %s from media package '%s' "
              + "in Microsoft Azure storage container '%s'.",
          track.getURI(), mpId, azureContainerName), e);
    }
    // store transcription job ID and status
    try {
      database.updateJobControl(transcription.getID(), TranscriptionJobControl.Status.InProgress.name());
    } catch (TranscriptionDatabaseException e) {
      throw new TranscriptionServiceException(String.format(
          "Unable to store transcription job of track %s from media package '%s' in the database.",
          track.getURI(), mpId), e);
    }
    // return transcription job ID
    return transcription.getID();
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
