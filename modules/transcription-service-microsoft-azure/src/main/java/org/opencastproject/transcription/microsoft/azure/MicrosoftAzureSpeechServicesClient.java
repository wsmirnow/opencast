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

import org.opencastproject.transcription.microsoft.azure.model.MicrosoftAzureSpeechServicesErrorResponse;
import org.opencastproject.transcription.microsoft.azure.model.MicrosoftAzureSpeechTranscription;
import org.opencastproject.transcription.microsoft.azure.model.MicrosoftAzureSpeechTranscriptions;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.istack.NotNull;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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

  public List<MicrosoftAzureSpeechTranscription> getTranscriptions(int skip, int top)
          throws IOException, MicrosoftAzureNotAllowedException, MicrosoftAzureSpeechClientException {
    return getTranscriptions(0, 0, null);
  }

  public List<MicrosoftAzureSpeechTranscription> getTranscriptions(int skip, int top, String filter)
          throws IOException, MicrosoftAzureNotAllowedException, MicrosoftAzureSpeechClientException {
    StringBuilder url = new StringBuilder(azureSpeechServicesEndpoint + "/speechtotext/v3.1/transcriptions");
    StringBuilder params = new StringBuilder();
    if (skip > 0) {
      params.append("skip=" + skip);
    }
    if (top > 0) {
      params.append("top=" + top);
    }
    if (StringUtils.isNotBlank(filter)) {
      params.append("filter=" + URLEncoder.encode(filter, StandardCharsets.UTF_8));
    }
    if (params.length() > 0) {
      url.append("?");
      url.append(params);
    }
    try (CloseableHttpClient httpClient = HttpUtils.makeHttpClient()) {
      HttpGet httpGet = new HttpGet(url.toString());
      httpGet.addHeader("Ocp-Apim-Subscription-Key", azureCognitiveServicesSubscriptionKey);
      try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
        int code = response.getStatusLine().getStatusCode();
        String responseString = EntityUtils.toString(response.getEntity());
        Gson gson = new GsonBuilder().create();
        MicrosoftAzureSpeechServicesErrorResponse errorResponse;
        switch (code) {
          case HttpStatus.SC_OK: // 200
            break;
          case HttpStatus.SC_FORBIDDEN: // 403
            errorResponse = gson.fromJson(responseString, MicrosoftAzureSpeechServicesErrorResponse.class);
            throw new MicrosoftAzureNotAllowedException(String.format("Not allowed to get transcriptions. "
                + "Microsoft Azure Speech Services error code %d: %s", errorResponse.error.code,
                errorResponse.error.message));
          default:
            errorResponse = gson.fromJson(responseString, MicrosoftAzureSpeechServicesErrorResponse.class);
            throw new MicrosoftAzureSpeechClientException(String.format(
                "Getting transcriptions failed with HTTP response code %d. "
                    + "Microsoft Azure Speech Services error code %d: %s", code, errorResponse.error.code,
                errorResponse.error.message));
        }
        MicrosoftAzureSpeechTranscriptions transcriptions = gson.fromJson(responseString,
            MicrosoftAzureSpeechTranscriptions.class);
        return transcriptions.values;
      }
    }
  }

  public MicrosoftAzureSpeechTranscription getTranscription(@NotNull String transcriptionId)
          throws IOException, MicrosoftAzureNotAllowedException, MicrosoftAzureSpeechClientException {
    if (StringUtils.isBlank(transcriptionId)) {
      throw new IllegalArgumentException("Transcription ID not set.");
    }
    String url = azureSpeechServicesEndpoint + "/speechtotext/v3.1/transcriptions/"
        + StringUtils.trimToEmpty(transcriptionId);
    try (CloseableHttpClient httpClient = HttpUtils.makeHttpClient()) {
      HttpGet httpGet = new HttpGet(url);
      httpGet.addHeader("Ocp-Apim-Subscription-Key", azureCognitiveServicesSubscriptionKey);
      try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
        int code = response.getStatusLine().getStatusCode();
        String responseString = EntityUtils.toString(response.getEntity());
        Gson gson = new GsonBuilder().create();
        MicrosoftAzureSpeechServicesErrorResponse errorResponse;
        switch (code) {
          case HttpStatus.SC_OK: // 200
            break;
          case HttpStatus.SC_FORBIDDEN: // 403
            errorResponse = gson.fromJson(responseString, MicrosoftAzureSpeechServicesErrorResponse.class);
            throw new MicrosoftAzureNotAllowedException(String.format("Not allowed to get transcription with ID '%s'. "
                    + "Microsoft Azure Speech Services error code %d: %s", transcriptionId, errorResponse.error.code,
                errorResponse.error.message));
          default:
            errorResponse = gson.fromJson(responseString, MicrosoftAzureSpeechServicesErrorResponse.class);
            throw new MicrosoftAzureSpeechClientException(String.format(
                "Getting transcription with ID '%s' failed with HTTP response code %d. "
                    + "Microsoft Azure Speech Services error code %d: %s", transcriptionId, code,
                errorResponse.error.code, errorResponse.error.message));
        }
        return gson.fromJson(responseString, MicrosoftAzureSpeechTranscription.class);
      }
    }
  }
}
