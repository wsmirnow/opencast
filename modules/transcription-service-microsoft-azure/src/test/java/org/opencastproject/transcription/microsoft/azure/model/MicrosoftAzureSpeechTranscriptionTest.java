package org.opencastproject.transcription.microsoft.azure.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mchange.io.FileUtils;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;

@RunWith(Parameterized.class)
public class MicrosoftAzureSpeechTranscriptionTest {

  private final String testResourcePath;
  private Gson gson;

  public MicrosoftAzureSpeechTranscriptionTest(String testResourcePath) {
    this.testResourcePath = testResourcePath;
  }

  @Before
  public void setUp() {
    gson = new GsonBuilder().create();
  }

  @Parameterized.Parameters()
  public static List<String> data() {
    return Arrays.asList("/transcription1.json", "/transcription2.json", "/transcription3.json");
  }

  @Test
  public void deserialize() throws URISyntaxException, IOException {
    String transcriptionStr = FileUtils.getContentsAsString(new File(MicrosoftAzureSpeechTranscriptionTest.class
        .getResource(testResourcePath).toURI()));
    MicrosoftAzureSpeechTranscription transcription = gson.fromJson(transcriptionStr,
        MicrosoftAzureSpeechTranscription.class);
    Assume.assumeNotNull(transcription);
  }
}