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

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.HmacAlgorithms;
import org.apache.commons.codec.digest.HmacUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import javax.crypto.Mac;

public class MicrosoftAzureAuthorization {

  private static final Logger logger = LoggerFactory.getLogger(MicrosoftAzureStorageClient.class);

  public static final long MILLIS_IN_A_DAY = 1000 * 60 * 60 * 24;
  public static final long MILLIS_IN_A_MINUTE = 1000 * 60;
  public static final String AZURE_STORAGE_VERSION = "2022-11-02";
  public static final String AZURE_BLOB_STORE_URL_SUFFIX = "blob.core.windows.net";

  private final String azureStorageAccountName;
  private final String azureAccountAccessKey;

  public MicrosoftAzureAuthorization(String azureStorageAccountName, String azureAccountAccessKey)
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

  public String getAzureStorageAccountName() {
    return azureStorageAccountName;
  }

  String generateAccountSASToken(String signedPermissions, String signedResourceType,
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
    stringBuilder.append(AZURE_STORAGE_VERSION + "\n");
    queryArgs.add("sv=" + AZURE_STORAGE_VERSION);
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
}
