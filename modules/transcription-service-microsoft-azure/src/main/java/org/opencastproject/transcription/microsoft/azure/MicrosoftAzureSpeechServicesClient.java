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

import org.opencastproject.transcription.microsoft.azure.model.MicrosoftAzureSpeechTranscription;

import com.sun.istack.NotNull;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class MicrosoftAzureSpeechServicesClient {

  private static final Logger logger = LoggerFactory.getLogger(MicrosoftAzureSpeechServicesClient.class);

  private final String azureSpeechServicesEndpoint;
  private final String azureCognitiveServicesSubscriptionKey;

  public MicrosoftAzureSpeechServicesClient(@NotNull String azureSpeechServicesEndpoint,
      @NotNull String azureCognitiveServicesSubscriptionKey) {

    this.azureSpeechServicesEndpoint = StringUtils.trimToEmpty(azureSpeechServicesEndpoint);
    this.azureCognitiveServicesSubscriptionKey = StringUtils.trimToEmpty(azureCognitiveServicesSubscriptionKey);
  }

  public List<MicrosoftAzureSpeechTranscription> getTranscriptions() {
    return getTranscriptions(0, 0);
  }
  public List<MicrosoftAzureSpeechTranscription> getTranscriptions(int skip, int top) {
    return null;
  }

  public MicrosoftAzureSpeechTranscription getTranscription(@NotNull String transcriptionId) {
    return null;
  }
}
