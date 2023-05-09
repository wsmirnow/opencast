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
package org.opencastproject.transcription.microsoft.azure.model;

import com.sun.istack.NotNull;

import org.apache.commons.lang3.StringUtils;

import java.util.Dictionary;
import java.util.List;

public class MicrosoftAzureSpeechTranscription {

  // Documentation:
  // https://eastus.dev.cognitive.microsoft.com/docs/services/speech-to-text-api-v3-1/operations/Transcriptions_Get

  public class TranscriptionFiles {
    public String files;

    /** Default constructor */
    public TranscriptionFiles() { }
  }
  public TranscriptionFiles links;
  public Dictionary<String, Object> properties;
  public String self;
  public Dictionary<String, Object> model;
  public Dictionary<String, Object> project;
  public Dictionary<String, Object> dataset;
  public List<String> contentUrls;
  public String contentContainerUrl;
  public String locale;
  public String displayName;
  public String description;
  public Dictionary<String, Object> customProperties;
  public String lastActionDateTime;
  public String status;
  public String createdDateTime;

  /** Default constructor. */
  public MicrosoftAzureSpeechTranscription() { }

  public boolean isFailed() {
    return StringUtils.equalsIgnoreCase("Failed", status);
  }

  public boolean isRunning() {
    return StringUtils.equalsIgnoreCase("Running", status);
  }

  public boolean isSucceeded() {
    return StringUtils.equalsIgnoreCase("Succeeded", status);
  }
}
