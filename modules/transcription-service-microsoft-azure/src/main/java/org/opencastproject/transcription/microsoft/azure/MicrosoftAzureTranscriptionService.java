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
import org.opencastproject.mediapackage.Track;
import org.opencastproject.security.api.OrganizationDirectoryService;
import org.opencastproject.security.api.SecurityService;
import org.opencastproject.security.api.UserDirectoryService;
import org.opencastproject.serviceregistry.api.ServiceRegistry;
import org.opencastproject.transcription.api.TranscriptionService;
import org.opencastproject.transcription.api.TranscriptionServiceException;
import org.opencastproject.transcription.persistence.TranscriptionDatabase;
import org.opencastproject.workflow.api.WorkflowService;
import org.opencastproject.workingfilerepository.api.WorkingFileRepository;
import org.opencastproject.workspace.api.Workspace;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

@Component(immediate = true, service = { TranscriptionService.class,
    MicrosoftAzureTranscriptionService.class }, property = {
    "service.description=Microsoft Azure Transcription Service", "provider=microsoft.azure" })
public class MicrosoftAzureTranscriptionService extends AbstractJobProducer implements TranscriptionService {

  private static final Logger logger = LoggerFactory.getLogger(MicrosoftAzureTranscriptionService.class);

  private static final String JOB_TYPE = "org.opencastproject.transcription.microsoft.azure";

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

  private enum Operation {
    StartTranscription
  }

  /**
   * A public constructor, required by OSGi.
   */
  public MicrosoftAzureTranscriptionService() {
    super(JOB_TYPE);
  }

  @Override
  protected String process(Job job) throws Exception {
    return null;
  }

  @Override
  public Job startTranscription(String mpId, Track track) throws TranscriptionServiceException {
    return null;
  }

  @Override
  public Job startTranscription(String mpId, Track track, String... args) throws TranscriptionServiceException {
    return null;
  }

  @Override
  public MediaPackageElement getGeneratedTranscription(String mpId, String jobId) throws TranscriptionServiceException {
    return null;
  }

  @Override
  public void transcriptionDone(String mpId, Object results) throws TranscriptionServiceException {

  }

  @Override
  public void transcriptionError(String mpId, Object results) throws TranscriptionServiceException {

  }

  @Override
  public String getLanguage() {
    return null;
  }

  @Override
  public Map<String, Object> getReturnValues(String mpId, String jobId) throws TranscriptionServiceException {
    return null;
  }

  @Override
  protected ServiceRegistry getServiceRegistry() {
    return this.serviceRegistry;
  }

  @Override
  protected SecurityService getSecurityService() {
    return this.securityService;
  }

  @Override
  protected UserDirectoryService getUserDirectoryService() {
    return this.userDirectoryService;
  }

  @Override
  protected OrganizationDirectoryService getOrganizationDirectoryService() {
    return this.organizationDirectoryService;
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
