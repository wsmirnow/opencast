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

import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

public class MicrosoftAzureSpeechServicesClientTest {

  private static final Logger logger = LoggerFactory.getLogger(MicrosoftAzureSpeechServicesClientTest.class);

  private boolean enabled;
  private String azureSpeechServicesEndpoint;
  private String azureCognitiveServicesSubscriptionKey;
  private MicrosoftAzureSpeechServicesClient azureSpeechClient;


  @Before
  public void setUp() throws Exception {
    azureSpeechServicesEndpoint = "https://videountertitelung-cognitive-service.cognitiveservices.azure.com";
    azureCognitiveServicesSubscriptionKey = "";
    enabled = StringUtils.isNotBlank(azureSpeechServicesEndpoint)
        && StringUtils.isNotBlank(azureCognitiveServicesSubscriptionKey);
    azureSpeechClient = new MicrosoftAzureSpeechServicesClient(azureSpeechServicesEndpoint,
        azureCognitiveServicesSubscriptionKey);
  }

  @Test
  public void getTranscriptions()
          throws MicrosoftAzureNotAllowedException, IOException, MicrosoftAzureSpeechClientException {
    if (!enabled) {
      return;
    }
    List<MicrosoftAzureSpeechTranscription> transcriptions = azureSpeechClient.getTranscriptions(0, 0, null);
    Assert.assertNotNull(transcriptions);
  }

  @Test
  public void getTranscription() {
  }
}
