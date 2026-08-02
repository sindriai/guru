package com.sindriai.guru.data.asr;

import java.io.IOException;

public interface WhisperEngineInterface {
    boolean isInitialized();
    boolean initialize(String modelPath, String vocabPath, boolean multilingual) throws IOException;
    void deInitialize();
    String transcribeFile(String wavePath);
    String transcribeBuffer(float[] samples);
}
