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

import com.sun.istack.NotNull;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class MicrosoftAzureStorageClient {

  private static final Logger logger = LoggerFactory.getLogger(MicrosoftAzureStorageClient.class);

  private static final int CONNECTION_TIMEOUT = 1000 * 60;
  private static final int SOCKET_TIMEOUT = 1000 * 300;

  private MicrosoftAzureAuthorization azureAuthorization;

  public MicrosoftAzureStorageClient(@NotNull MicrosoftAzureAuthorization azureAuthorization) {
    this.azureAuthorization = azureAuthorization;
  }

  public boolean containerExists(String mpId, String azureContainerName)
          throws MicrosoftAzureStorageClientException, IOException, MicrosoftAzureNotAllowedException {
    try {
      Map<String, String> containerProperties = getContainerProperties(mpId, azureContainerName);
      return containerProperties.containsKey("x-ms-blob-public-access") && StringUtils.equalsIgnoreCase("unlocked",
          containerProperties.getOrDefault("x-ms-lease-status", "INVALID"));
    } catch (MicrosoftAzureNotFoundException ex) {
      return false;
    }
  }

  public Map<String, String> getContainerProperties(String mpId, String azureContainerName)
          throws MicrosoftAzureStorageClientException, IOException, MicrosoftAzureNotAllowedException,
          MicrosoftAzureNotFoundException {
    String containerUrl = String.format("https://%s.%s/%s?%s", azureAuthorization.getAzureStorageAccountName(),
        MicrosoftAzureAuthorization.AZURE_BLOB_STORE_URL_SUFFIX,
        StringUtils.trimToEmpty(azureContainerName), "restype=container");
    String sasToken = azureAuthorization.generateAccountSASToken("r", "c",
        null, null, null, null);
    containerUrl = containerUrl + "&" + sasToken;

    try (CloseableHttpClient httpClient = makeHttpClient(CONNECTION_TIMEOUT, SOCKET_TIMEOUT, CONNECTION_TIMEOUT)) {
      HttpGet httpGet = new HttpGet(containerUrl);
      try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
        int code = response.getStatusLine().getStatusCode();
        Map<String, String> headersMap = Arrays.stream(response.getAllHeaders())
            .collect(Collectors.toMap(NameValuePair::getName, NameValuePair::getValue));
        switch (code) {
          case HttpStatus.SC_OK: // 200
            break;
          case HttpStatus.SC_FORBIDDEN: // 403
            throw new MicrosoftAzureNotAllowedException(String.format(
                "Not allowed to read Azure storage container properties for container %s. Microsoft error code: %s",
                azureContainerName, headersMap.getOrDefault("x-ms-error-code", "UNKNOWN")));
          case HttpStatus.SC_NOT_FOUND: // 404
            throw new MicrosoftAzureNotFoundException(
                String.format("Azure storage container %s does not exists. Microsoft error code: %s",
                    azureContainerName, headersMap.getOrDefault("x-ms-error-code", "UNKNOWN")));
          default:
            throw new MicrosoftAzureStorageClientException(String.format(
                "Getting Azure storage container metadata failed with HTTP response code %d for container %s. "
                    + "Microsoft error code: %s",
                code, azureContainerName, headersMap.getOrDefault("x-ms-error-code", "UNKNOWN")));
        }
        return headersMap;
      }
    }
  }

  public void createContainer(String mpId, String azureContainerName)
          throws MicrosoftAzureStorageClientException, IOException, MicrosoftAzureNotAllowedException {
    if (containerExists(mpId, azureContainerName)) {
      return;
    }
    String containerUrl = String.format("https://%s.%s/%s?%s", azureAuthorization.getAzureStorageAccountName(),
        MicrosoftAzureAuthorization.AZURE_BLOB_STORE_URL_SUFFIX, StringUtils.trimToEmpty(azureContainerName),
        "restype=container");
    String sasToken = azureAuthorization.generateAccountSASToken("w", "c", null, null, null, null);
    containerUrl = containerUrl + "&" + sasToken;
    try (CloseableHttpClient httpClient = makeHttpClient(CONNECTION_TIMEOUT, SOCKET_TIMEOUT, CONNECTION_TIMEOUT)) {
      HttpPut httpPut = new HttpPut(containerUrl);
      httpPut.addHeader("x-ms-blob-public-access", "blob");
      try (CloseableHttpResponse response = httpClient.execute(httpPut)) {
        int code = response.getStatusLine().getStatusCode();
        Map<String, String> headersMap = Arrays.stream(response.getAllHeaders())
            .collect(Collectors.toMap(NameValuePair::getName, NameValuePair::getValue));
        switch (code) {
          case HttpStatus.SC_CREATED: // 201
            break;
          case HttpStatus.SC_FORBIDDEN: // 403
            throw new MicrosoftAzureNotAllowedException(String.format(
                "Not allowed to read Azure storage container properties for container %s. Microsoft error code: %s",
                azureContainerName, headersMap.getOrDefault("x-ms-error-code", "UNKNOWN")));
          default:
            throw new MicrosoftAzureStorageClientException(String.format(
                "Creating Azure storage container %s failed with HTTP response code %d. Microsoft error code: %s",
                azureContainerName, code, headersMap.getOrDefault("x-ms-error-code", "UNKNOWN")));
        }
      }
    }
  }

  public String uploadFile(String mpId, File trackFile, String azureContainerName, String azureBlobPath)
          throws MicrosoftAzureStorageClientException, IOException, MicrosoftAzureNotAllowedException {
    String containerUrl = String.format("https://%s.%s/%s", azureAuthorization.getAzureStorageAccountName(),
        MicrosoftAzureAuthorization.AZURE_BLOB_STORE_URL_SUFFIX, StringUtils.trimToEmpty(azureContainerName));
    String blobUrl = containerUrl;
    if (!StringUtils.startsWith(azureBlobPath, "/")) {
      blobUrl += "/";
    }
    blobUrl += azureBlobPath;
    int blockSize = 100000000; // 100MB
    String sasToken = azureAuthorization.generateAccountSASToken("w", "o", null, null, null, null);
    try (FileInputStream trackStream = new FileInputStream(trackFile)) {
      try (CloseableHttpClient httpClient = makeHttpClient(CONNECTION_TIMEOUT, SOCKET_TIMEOUT, CONNECTION_TIMEOUT)) {
        List<String> blockIds = new ArrayList<>();
        // put blocks (file chunks)
        for (int iteration = 0; iteration * blockSize < trackFile.length(); iteration++) {
          String blockId = Base64.encodeBase64String(UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
          String putBlockUrl = blobUrl + "?comp=block&blockid="
              + URLEncoder.encode(blockId, StandardCharsets.UTF_8) + "&" + sasToken;
          HttpPut httpPut = new HttpPut(putBlockUrl);
          byte[] blockData = trackStream.readNBytes(blockSize);
          httpPut.setEntity(new ByteArrayEntity(blockData, ContentType.APPLICATION_OCTET_STREAM));
          try (CloseableHttpResponse response = httpClient.execute(httpPut)) {
            int code = response.getStatusLine().getStatusCode();
            Map<String, String> headersMap = Arrays.stream(response.getAllHeaders())
                .collect(Collectors.toMap(NameValuePair::getName, NameValuePair::getValue));
            switch (code) {
              case HttpStatus.SC_CREATED: // 201
                blockIds.add(blockId);
                break;
              case HttpStatus.SC_FORBIDDEN: // 403
                throw new MicrosoftAzureNotAllowedException(String.format(
                    "Not allowed to put block to Azure storage container %s. Microsoft error code: %s",
                    azureContainerName, headersMap.getOrDefault("x-ms-error-code", "UNKNOWN")));
              default:
                throw new MicrosoftAzureStorageClientException(String.format(
                    "Putting block to Azure storage container %s failed with HTTP response code %d. "
                        + "Microsoft error code: %s", azureContainerName, code,
                    headersMap.getOrDefault("x-ms-error-code", "UNKNOWN")));
            }
          }
        }
        // commit block list
        StringBuffer blockList = new StringBuffer();
        blockList.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
        blockList.append("<BlockList>");
        for (String blockId : blockIds) {
          blockList.append("<Uncommitted>");
          blockList.append(blockId);
          blockList.append("</Uncommitted>");
        }
        blockList.append("</BlockList>");
        String putBlockListUrl = blobUrl + "?comp=blocklist&" + sasToken;
        HttpPut httpPut = new HttpPut(putBlockListUrl);
        httpPut.setEntity(new StringEntity(blockList.toString(), "application/xml", "UTF-8"));
        try (CloseableHttpResponse response = httpClient.execute(httpPut)) {
          int code = response.getStatusLine().getStatusCode();
          Map<String, String> headersMap = Arrays.stream(response.getAllHeaders())
              .collect(Collectors.toMap(NameValuePair::getName, NameValuePair::getValue));
          switch (code) {
            case HttpStatus.SC_CREATED: // 201
              break;
            case HttpStatus.SC_FORBIDDEN: // 403
              throw new MicrosoftAzureNotAllowedException(String.format(
                  "Not allowed to put block list to Azure storage container %s. Microsoft error code: %s",
                  azureContainerName, headersMap.getOrDefault("x-ms-error-code", "UNKNOWN")));
            default:
              throw new MicrosoftAzureStorageClientException(String.format(
                  "Putting block list to Azure storage container %s failed with HTTP response code %d. "
                      + "Microsoft error code: %s", azureContainerName, code,
                  headersMap.getOrDefault("x-ms-error-code", "UNKNOWN")));
          }
        }
      }
    }
    return blobUrl;
  }

  protected CloseableHttpClient makeHttpClient(int conectionTimeout, int socketTimeout, int connectionRequestTimeout) {
    RequestConfig reqConfig = RequestConfig.custom().setConnectTimeout(conectionTimeout)
        .setSocketTimeout(socketTimeout)
        .setConnectionRequestTimeout(connectionRequestTimeout)
        .build();
    CloseableHttpClient httpClient = HttpClientBuilder.create()
        .useSystemProperties()
        .setDefaultRequestConfig(reqConfig)
        .build();
    return httpClient;
  }
}
