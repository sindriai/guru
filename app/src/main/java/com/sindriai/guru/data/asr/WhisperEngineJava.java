package com.sindriai.guru.data.asr;

import android.content.Context;
import android.os.Environment;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;

import com.sindriai.guru.core.util.WaveUtil;
import com.sindriai.guru.core.util.WhisperUtil;

public class WhisperEngineJava implements WhisperEngineInterface {

    private final WhisperUtil whisperUtil = new WhisperUtil();
    private final Context context;
    private boolean isInitialized = false;
    private Interpreter interpreter;

    public WhisperEngineJava(Context context) {
        this.context = context;
    }

    @Override
    public boolean isInitialized() {
        return isInitialized;
    }

    @Override
    public boolean initialize(String modelPath, String vocabPath, boolean multilingual) throws IOException {
        loadModel(modelPath);
        if (whisperUtil.loadFiltersAndVocab(multilingual, vocabPath)) {
            isInitialized = true;
        } else {
            isInitialized = false;
        }
        return isInitialized;
    }

    @Override
    public void deInitialize() {
        if (interpreter != null) {
            interpreter.close();
            interpreter = null;
        }
    }

    @Override
    public String transcribeFile(String wavePath) {
        float[] melSpectrogram = getMelSpectrogram(wavePath);
        return runInference(melSpectrogram);
    }

    @Override
    public String transcribeBuffer(float[] samples) {
        return null; // Not implemented
    }

    private void loadModel(String modelPath) throws IOException {
        try (FileInputStream fis = new FileInputStream(modelPath);
             FileChannel channel = fis.getChannel()) {

            ByteBuffer modelBuffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
            Interpreter.Options options = new Interpreter.Options();
            options.setNumThreads(Runtime.getRuntime().availableProcessors());
            interpreter = new Interpreter(modelBuffer, options);
        }
    }

    private float[] getMelSpectrogram(String wavePath) {
        float[] samples = WaveUtil.getSamples(wavePath);
        int fixedInputSize = WhisperUtil.WHISPER_SAMPLE_RATE * WhisperUtil.WHISPER_CHUNK_SIZE;
        float[] inputSamples = new float[fixedInputSize];
        System.arraycopy(samples, 0, inputSamples, 0, Math.min(samples.length, fixedInputSize));
        return whisperUtil.getMelSpectrogram(inputSamples, inputSamples.length,
                Runtime.getRuntime().availableProcessors());
    }

    private void saveMelSpectrogramToFile(float[] melSpectrogram) {
        File directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        if (!directory.exists()) directory.mkdirs();
        File file = new File(directory, "mel_spectrogram_" + System.currentTimeMillis() + ".txt");

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file)))) {
            for (float value : melSpectrogram) {
                writer.write(String.valueOf(value));
                writer.newLine();
            }
        } catch (IOException ignored) {}
    }

    private String runInference(float[] inputData) {
        Tensor inputTensor = interpreter.getInputTensor(0);
        TensorBuffer inputBuffer = TensorBuffer.createFixedSize(inputTensor.shape(), inputTensor.dataType());

        ByteBuffer inputBuf = ByteBuffer.allocateDirect(
                inputTensor.shape()[0] * inputTensor.shape()[1] * inputTensor.shape()[2] * Float.BYTES
        ).order(ByteOrder.nativeOrder());

        for (float input : inputData) {
            inputBuf.putFloat(input);
        }
        inputBuffer.loadBuffer(inputBuf);

        Tensor outputTensor = interpreter.getOutputTensor(0);
        TensorBuffer outputBuffer = TensorBuffer.createFixedSize(outputTensor.shape(), DataType.FLOAT32);

        interpreter.run(inputBuffer.getBuffer(), outputBuffer.getBuffer());

        ByteBuffer outputBuf = outputBuffer.getBuffer();
        StringBuilder result = new StringBuilder();
        int outputLen = outputBuffer.getIntArray().length;

        for (int i = 0; i < outputLen; i++) {
            int token = outputBuf.getInt();
            if (token == whisperUtil.getTokenEOT()) break;
            if (token < whisperUtil.getTokenEOT()) {
                result.append(whisperUtil.getWordFromToken(token));
            }
        }

        return result.toString();
    }
}
