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
import org.apache.commons.codec.digest.HmacAlgorithms;
import org.apache.commons.codec.digest.HmacUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.stream.Collectors;

import javax.crypto.Mac;

public class MicrosoftAzureStorageClient {

  private static final Logger logger = LoggerFactory.getLogger(MicrosoftAzureStorageClient.class);

  private static final long MILLIS_IN_A_DAY = 1000 * 60 * 60 * 24;
  private static final long MILLIS_IN_A_MINUTE = 1000 * 60;
  private static final int CONNECTION_TIMEOUT = 1000 * 60;
  private static final int SOCKET_TIMEOUT = 1000 * 300;

  private String azureStorageAccountName;
  private String azureAccountAccessKey;
  private String azureBlobStoreUrlSuffix = "blob.core.windows.net";
  //private String azureStorageVersion = "2020-08-04";
  private String azureStorageVersion = "2022-11-02";

  public MicrosoftAzureStorageClient(String azureStorageAccountName, String azureAccountAccessKey)
          throws MicrosoftAzureStorageClientException {
    this.azureStorageAccountName = StringUtils.trimToEmpty(azureStorageAccountName);
    this.azureAccountAccessKey = StringUtils.trimToEmpty(azureAccountAccessKey);
    if (StringUtils.isEmpty(azureStorageAccountName)) {
      throw new MicrosoftAzureStorageClientException("Azure storage account name not set.");
    }
    if (StringUtils.isEmpty(azureAccountAccessKey)) {
      throw new MicrosoftAzureStorageClientException("Azure storage account key not set.");
    }
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
    String containerUrl = String.format("https://%s.%s/%s?%s", azureStorageAccountName, azureBlobStoreUrlSuffix,
        StringUtils.trimToEmpty(azureContainerName), "restype=container");
    String sasToken = generateAccountSASToken("r", "c",
        null, null, null, null);
    containerUrl = containerUrl + "&" + sasToken;

    try (CloseableHttpClient httpClient = makeHttpClient(CONNECTION_TIMEOUT, SOCKET_TIMEOUT, CONNECTION_TIMEOUT)) {
      HttpGet httpGet = new HttpGet(containerUrl);
      CloseableHttpResponse response = httpClient.execute(httpGet);
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
          throw new MicrosoftAzureNotFoundException(String.format(
              "Azure storage container %s does not exists. Microsoft error code: %s",
              azureContainerName, headersMap.getOrDefault("x-ms-error-code", "UNKNOWN")));
        default:
          throw new MicrosoftAzureStorageClientException(String.format(
              "Getting Azure storage container metadata failed with HTTP response code %d. Microsoft error code: %s",
              code,  headersMap.getOrDefault("x-ms-error-code", "UNKNOWN")));
      }
      return headersMap;
    }
  }

  public void createContainer(String mpId, String azureContainerName)
          throws MicrosoftAzureStorageClientException, IOException, MicrosoftAzureNotAllowedException {
    if (containerExists(mpId, azureContainerName)) {
      return;
    }
    String containerUrl = String.format("https://%s.%s/%s?%s", azureStorageAccountName, azureBlobStoreUrlSuffix,
        StringUtils.trimToEmpty(azureContainerName), "restype=container");
    String sasToken = generateAccountSASToken("w", "c",
        null, null, null, null);
    containerUrl = containerUrl + "&" + sasToken;
    try (CloseableHttpClient httpClient = makeHttpClient(CONNECTION_TIMEOUT, SOCKET_TIMEOUT, CONNECTION_TIMEOUT)) {
      HttpPut httpPut = new HttpPut(containerUrl);
      httpPut.addHeader("x-ms-blob-public-access", "blob");
      CloseableHttpResponse response = httpClient.execute(httpPut);
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
              "Creating Azure storage container failed with HTTP response code %d. Microsoft error code: %s", code,
              headersMap.getOrDefault("x-ms-error-code", "UNKNOWN")));
      }
    }
  }

  public String uploadFile(String mpId, File trackFile, String azureContainerName, String azureBlobPath)
          throws MicrosoftAzureStorageClientException, IOException {
    return "";
  }

  String generateAccountSASToken(@NotNull String signedPermissions, @NotNull String signedResourceType,
      Date signedStart, Date signedExpiry, String signedIP, String signedEncryptionScope) {
    // documentation: https://learn.microsoft.com/en-us/rest/api/storageservices/create-account-sas
    SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    df.setTimeZone(TimeZone.getTimeZone("UTC"));
    List<String> queryArgs = new ArrayList<>();
    StringBuilder stringBuilder = new StringBuilder();
    /*
    StringToSign = accountname + "\n" +
        signedpermissions + "\n" +
        signedservice + "\n" +
        signedresourcetype + "\n" +
        signedstart + "\n" +
        signedexpiry + "\n" +
        signedIP + "\n" +
        signedProtocol + "\n" +
        signedversion + "\n" +
        signedEncryptionScope + "\n"
    */
    stringBuilder.append(azureStorageAccountName + "\n");
    stringBuilder.append(StringUtils.trimToEmpty(signedPermissions) + "\n");
    queryArgs.add("sp=" + StringUtils.trimToEmpty(signedPermissions));
    stringBuilder.append("b" + "\n");
    queryArgs.add("ss=b");
    stringBuilder.append(StringUtils.trimToEmpty(signedResourceType) + "\n");
    queryArgs.add("srt=" + StringUtils.trimToEmpty(signedResourceType));
    if (signedStart == null) {
      Date startDate = new Date(Calendar.getInstance().getTimeInMillis() + (-15 * MILLIS_IN_A_MINUTE));
      stringBuilder.append(df.format(startDate) + "\n");
      queryArgs.add("st=" + df.format(startDate));
    } else {
      stringBuilder.append(df.format(signedStart) + "\n");
      queryArgs.add("st=" + df.format(signedStart));
    }
    if (signedExpiry == null) {
      Date endDate = new Date(Calendar.getInstance().getTimeInMillis() + (1 * MILLIS_IN_A_DAY));
      stringBuilder.append(df.format(endDate) + "\n");
      queryArgs.add("se=" + df.format(endDate));
    } else {
      stringBuilder.append(df.format(signedExpiry) + "\n");
      queryArgs.add("se=" + df.format(signedExpiry));
    }
    stringBuilder.append(StringUtils.trimToEmpty(signedIP) + "\n");
    if (StringUtils.isNotBlank(signedIP)) {
      queryArgs.add("sip=" + StringUtils.trimToEmpty(signedIP));
    }
    stringBuilder.append("https" + "\n");
    queryArgs.add("spr=https");
    stringBuilder.append(azureStorageVersion + "\n");
    queryArgs.add("sv=" + azureStorageVersion);
    stringBuilder.append(StringUtils.trimToEmpty(signedEncryptionScope) + "\n");
    if (StringUtils.isNotBlank(signedEncryptionScope)) {
      queryArgs.add("ses=" + StringUtils.trimToEmpty(signedEncryptionScope));
    }
    String stringToSign = stringBuilder.toString();

    Mac initializedMac = HmacUtils.getInitializedMac(HmacAlgorithms.HMAC_SHA_256,
        Base64.decodeBase64(azureAccountAccessKey));
    byte[] signedString = initializedMac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
    String signature = Base64.encodeBase64String(signedString);
    queryArgs.add("sig=" + URLEncoder.encode(signature, StandardCharsets.UTF_8));
    return StringUtils.joinWith("&", queryArgs.toArray());
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
