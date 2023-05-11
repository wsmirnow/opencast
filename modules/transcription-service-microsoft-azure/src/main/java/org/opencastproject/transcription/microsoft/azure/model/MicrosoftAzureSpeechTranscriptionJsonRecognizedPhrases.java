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

import org.apache.commons.lang3.StringUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;

public class MicrosoftAzureSpeechTranscriptionJsonRecognizedPhrases {

  // CHECKSTYLE:OFF checkstyle:LineLength

  // Documentation:
  // https://eastus.dev.cognitive.microsoft.com/docs/services/speech-to-text-api-v3-1/operations/Transcriptions_ListFiles

  // CHECKSTYLE:ON checkstyle:LineLength
  // CHECKSTYLE:OFF checkstyle:VisibilityModifier

  public String recognitionStatus;
  public int channel;
  public String offset;
  public String duration;
  public long offsetInTicks;
  public long durationInTicks;
  public List<MicrosoftAzureSpeechTranscriptionJsonRecognizedPhrase> nBest;

  // CHECKSTYLE:ON checkstyle:VisibilityModifier

  public MicrosoftAzureSpeechTranscriptionJsonRecognizedPhrases() { }

  public String toSrt(float minConfidence) {
    String text = getBestRecognizedText(minConfidence);
    if (StringUtils.isNotBlank(text)) {
      return String.format("%s\n%s\n", getSegmentTimestamp(false), text);
    }
    return "";
  }

  public String toWebVtt(float minConfidence) {
    String text = getBestRecognizedText(minConfidence);
    if (StringUtils.isNotBlank(text)) {
      return String.format("%s\n%s\n", getSegmentTimestamp(true), text);
    }
    return "";
  }

  String getSegmentTimestamp(boolean formatWebVtt) {
    long ticksPerMillisecond = 10000;
    Date startTime = new Date(offsetInTicks / ticksPerMillisecond);
    Date endTime = new Date((offsetInTicks + durationInTicks) / ticksPerMillisecond);
    String format;
    if (formatWebVtt) {
      format = "HH:mm:ss.SSS";
    } else {
      // SRT format requires ',' as decimal separator rather than '.'.
      format = "HH:mm:ss,SSS";
    }
    SimpleDateFormat formatter = new SimpleDateFormat(format);
    // If we don't do this, the time is adjusted for our local time zone, which we don't want.
    formatter.setTimeZone(TimeZone.getTimeZone("GMT"));
    return String.format("%s --> %s", formatter.format(startTime), formatter.format(endTime));
  }

  public String getBestRecognizedText(float minConfidence) {
    if (nBest == null) {
      return null;
    }
    Optional<MicrosoftAzureSpeechTranscriptionJsonRecognizedPhrase> bestPhrase = nBest.stream()
        .filter(phrase -> phrase.confidence >= minConfidence)
        .sorted((t1, t2) -> Float.compare(t2.confidence, t1.confidence))  // descendant order
        .findFirst();
    return bestPhrase.isPresent() ? bestPhrase.get().display : "";
  }
}
