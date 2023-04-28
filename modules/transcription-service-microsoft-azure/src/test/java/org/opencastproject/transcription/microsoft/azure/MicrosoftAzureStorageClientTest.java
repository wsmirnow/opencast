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

import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.TimeZone;

public class MicrosoftAzureStorageClientTest {

  private static final Logger logger = LoggerFactory.getLogger(MicrosoftAzureStorageClientTest.class);

  /**
   * Provide the Microsoft Azure storage account name as value here or
   * put the value into AZURE_STORAGE_ACCOUNT_NAME environment variable.
   */
  private String azureStorageAccountName = "mmssubtitlestorage";
  /**
   * Provide the Microsoft Azure account access key as value here or
   * put the value into AZURE_ACCOUNT_ACCESS_KEY environment variable.
   */
  private String azureAccountAccessKey = "";
  private boolean enabled;   // will be set in setUp according the values of
                             // azureStorageAccountName and azureAccountAccessKey

  private MicrosoftAzureStorageClient azureStorageClient;

  @Before
  public void setUp() throws MicrosoftAzureStorageClientException {
//    if (StringUtils.isBlank(azureAccountAccessKey)) {
//      String envValue = System.getProperty("AZURE_STORAGE_ACCOUNT_NAME");
//      if (StringUtils.isNotBlank(envValue)) {
//        azureAccountAccessKey = envValue;
//      }
//    }
//    if (StringUtils.isBlank(azureAccountAccessKey)) {
//      String envValue = System.getProperty("AZURE_ACCOUNT_ACCESS_KEY");
//      if (StringUtils.isNotBlank(envValue)) {
//        azureAccountAccessKey = envValue;
//      }
//    }
    enabled = StringUtils.isNotBlank(azureStorageAccountName)
        && StringUtils.isNotBlank(azureAccountAccessKey);
    azureStorageClient = new MicrosoftAzureStorageClient(azureStorageAccountName, azureAccountAccessKey);
  }

  @Test
  public void containerExists() {
    if (!enabled) {
      return;
    }
  }

  @Test
  public void getContainerProperties() throws MicrosoftAzureStorageClientException, IOException {
    if (!enabled) {
      return;
    }
    Map<String, String> containerProps = azureStorageClient.getContainerProperties("mp1", "opencast-transcriptions");
    Assert.assertNotNull(containerProps);
    Assert.assertFalse(containerProps.isEmpty());
    logger.info("Container properties: {}", containerProps);
  }

  @Test
  public void createContainer() {
    if (!enabled) {
      return;
    }
  }

  @Test
  public void uploadFile() {
    if (!enabled) {
      return;
    }
  }

  @Test
  public void generateAccountSASToken() {
    Calendar c = Calendar.getInstance();
    c.set(2023,Calendar.JANUARY,15,12,0,0);
    c.setTimeZone(TimeZone.getTimeZone("GMT+1"));
    Date start = c.getTime();
    c.set(2023,Calendar.JANUARY,16,12,0,0);
    Date end = c.getTime();
    String sasToken = azureStorageClient.generateAccountSASToken("r", "c", start, end, null, null);
    Assert.assertNotNull(sasToken);
    Assert.assertTrue(sasToken.contains("sp=r"));
    Assert.assertTrue(sasToken.contains("srt=c"));
    Assert.assertTrue(sasToken.contains("st=2023-01-15T11:00:00Z"));
    Assert.assertTrue(sasToken.contains("se=2023-01-16T11:00:00Z"));
    Assert.assertTrue(sasToken.contains("spr=https"));
    Assert.assertTrue(sasToken.contains("sig=bX6zT1PlwSahNOJAjSARoRyvZ6fHPJGKvAqBdAJx8/4="));
  }
}
