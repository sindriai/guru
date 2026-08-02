package com.sindriai.guru.core.util;

import static java.lang.Math.cos;
import static java.lang.Math.log10;

import android.os.Build;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WhisperUtil {

    public static final int WHISPER_SAMPLE_RATE = 16000;
    public static final int WHISPER_N_FFT = 400;
    public static final int WHISPER_N_MEL = 80;
    public static final int WHISPER_HOP_LENGTH = 160;
    public static final int WHISPER_CHUNK_SIZE = 30;
    public static final int WHISPER_MEL_LEN = 3000;

    private final WhisperVocab vocab = new WhisperVocab();
    private final WhisperFilter filters = new WhisperFilter();
    private final WhisperMel mel = new WhisperMel();

    public int getTokenTranslate() {
        return vocab.tokenTRANSLATE;
    }

    public int getTokenTranscribe() {
        return vocab.tokenTRANSCRIBE;
    }

    public int getTokenEOT() {
        return vocab.tokenEOT;
    }

    public int getTokenSOT() {
        return vocab.tokenSOT;
    }

    public int getTokenPREV() {
        return vocab.tokenPREV;
    }

    public int getTokenSOLM() {
        return vocab.tokenSOLM;
    }

    public int getTokenNOT() {
        return vocab.tokenNOT;
    }

    public int getTokenBEG() {
        return vocab.tokenBEG;
    }

    public String getWordFromToken(int token) {
        return vocab.tokenToWord.get(token);
    }

    public boolean loadFiltersAndVocab(boolean multilingual, String vocabPath) throws IOException {
        byte[] bytes = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            bytes = Files.readAllBytes(Paths.get(vocabPath));
        }
        ByteBuffer vocabBuf = ByteBuffer.wrap(bytes);
        vocabBuf.order(ByteOrder.nativeOrder());

        int magic = vocabBuf.getInt();
        if (magic != 0x5553454e) {
            return false;
        }

        filters.nMel = vocabBuf.getInt();
        filters.nFft = vocabBuf.getInt();

        byte[] filterData = new byte[filters.nMel * filters.nFft * Float.BYTES];
        vocabBuf.get(filterData, 0, filterData.length);
        ByteBuffer filterBuf = ByteBuffer.wrap(filterData);
        filterBuf.order(ByteOrder.nativeOrder());

        filters.data = new float[filters.nMel * filters.nFft];
        for (int i = 0; filterBuf.hasRemaining(); i++) {
            filters.data[i] = filterBuf.getFloat();
        }

        int nVocab = vocabBuf.getInt();
        for (int i = 0; i < nVocab; i++) {
            int len = vocabBuf.getInt();
            byte[] wordBytes = new byte[len];
            vocabBuf.get(wordBytes, 0, wordBytes.length);
            String word = new String(wordBytes);
            vocab.tokenToWord.put(i, word);
        }

        int nVocabAdditional;
        if (!multilingual) {
            nVocabAdditional = vocab.nVocabEnglish;
        } else {
            nVocabAdditional = vocab.nVocabMultilingual;
            vocab.tokenEOT++;
            vocab.tokenSOT++;
            vocab.tokenPREV++;
            vocab.tokenSOLM++;
            vocab.tokenNOT++;
            vocab.tokenBEG++;
        }

        for (int i = nVocab; i < nVocabAdditional; i++) {
            String word;
            if (i > vocab.tokenBEG) {
                word = "[_TT_" + (i - vocab.tokenBEG) + "]";
            } else if (i == vocab.tokenEOT) {
                word = "[_EOT_]";
            } else if (i == vocab.tokenSOT) {
                word = "[_SOT_]";
            } else if (i == vocab.tokenPREV) {
                word = "[_PREV_]";
            } else if (i == vocab.tokenNOT) {
                word = "[_NOT_]";
            } else if (i == vocab.tokenBEG) {
                word = "[_BEG_]";
            } else {
                word = "[_extra_token_" + i + "]";
            }

            vocab.tokenToWord.put(i, word);
        }

        return true;
    }

    public float[] getMelSpectrogram(float[] samples, int nSamples, int nThreads) {
        int fftSize = WHISPER_N_FFT;
        int fftStep = WHISPER_HOP_LENGTH;

        mel.nMel = WHISPER_N_MEL;
        mel.nLen = nSamples / fftStep;
        mel.data = new float[mel.nMel * mel.nLen];

        float[] hann = new float[fftSize];
        for (int i = 0; i < fftSize; i++) {
            hann[i] = (float) (0.5 * (1.0 - cos(2.0 * Math.PI * i / (fftSize - 1))));
        }

        int nFft = 1 + fftSize / 2;

        List<Thread> workers = new ArrayList<>();
        for (int iw = 0; iw < nThreads; iw++) {
            final int ith = iw;
            Thread thread = new Thread(() -> {
                float[] fftIn = new float[fftSize];
                float[] fftOut = new float[fftSize * 2];

                for (int i = ith; i < mel.nLen; i += nThreads) {
                    int offset = i * fftStep;

                    for (int j = 0; j < fftSize; j++) {
                        if (offset + j < nSamples) {
                            fftIn[j] = hann[j] * samples[offset + j];
                        } else {
                            fftIn[j] = 0.0f;
                        }
                    }

                    fft2(fftIn, fftOut);
                    for (int j = 0; j < fftSize; j++) {
                        fftOut[j] = fftOut[2 * j] * fftOut[2 * j] + fftOut[2 * j + 1] * fftOut[2 * j + 1];
                    }

                    for (int j = 1; j < fftSize / 2; j++) {
                        fftOut[j] += fftOut[fftSize - j];
                    }

                    for (int j = 0; j < mel.nMel; j++) {
                        double sum = 0.0;
                        for (int k = 0; k < nFft; k++) {
                            sum += (fftOut[k] * filters.data[j * nFft + k]);
                        }

                        if (sum < 1e-10) {
                            sum = 1e-10;
                        }

                        sum = log10(sum);
                        mel.data[j * mel.nLen + i] = (float) sum;
                    }
                }

            });
            workers.add(thread);
            thread.start();
        }

        for (Thread worker : workers) {
            try {
                worker.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        double mmax = -1e20;
        for (int i = 0; i < mel.nMel * mel.nLen; i++) {
            if (mel.data[i] > mmax) {
                mmax = mel.data[i];
            }
        }

        mmax -= 8.0;
        for (int i = 0; i < mel.nMel * mel.nLen; i++) {
            if (mel.data[i] < mmax) {
                mel.data[i] = (float) mmax;
            }
            mel.data[i] = (float) ((mel.data[i] + 4.0) / 4.0);
        }

        return mel.data;
    }

    private static final int N = 400;
    private static final int N1 = 16;
    private static final int N2 = 25;
    private static final float[][] COS_25 = new float[N2][N2];
    private static final float[][] SIN_25 = new float[N2][N2];
    private static final float[][] COS_16 = new float[N1][N1];
    private static final float[][] SIN_16 = new float[N1][N1];
    private static final float[][] TW_COS = new float[N1][N2];
    private static final float[][] TW_SIN = new float[N1][N2];

    static {
        for (int k = 0; k < N2; k++)
            for (int n = 0; n < N2; n++) {
                double angle = -2 * Math.PI * k * n / N2;
                COS_25[k][n] = (float) Math.cos(angle);
                SIN_25[k][n] = (float) Math.sin(angle);
            }
        for (int k = 0; k < N1; k++)
            for (int n = 0; n < N1; n++) {
                double angle = -2 * Math.PI * k * n / N1;
                COS_16[k][n] = (float) Math.cos(angle);
                SIN_16[k][n] = (float) Math.sin(angle);
            }
        for (int n1 = 0; n1 < N1; n1++)
            for (int k2 = 0; k2 < N2; k2++) {
                double angle = -2 * Math.PI * n1 * k2 / N;
                TW_COS[n1][k2] = (float) Math.cos(angle);
                TW_SIN[n1][k2] = (float) Math.sin(angle);
            }
    }

    private void fft2(float[] input, float[] output) {
        float[] A = new float[2 * N];
        float[] B = new float[2 * N];
        for (int t = 0; t < N; t++) {
            A[2 * t] = input[t];
            A[2 * t + 1] = 0f;
        }

        for (int n1 = 0; n1 < N1; n1++) {
            for (int k2 = 0; k2 < N2; k2++) {
                float sumRe = 0f, sumIm = 0f;
                for (int n2 = 0; n2 < N2; n2++) {
                    int idx = 2 * (n1 + N1 * n2);
                    float a = A[idx];
                    float b = A[idx + 1];
                    float c = COS_25[k2][n2];
                    float s = SIN_25[k2][n2];
                    sumRe += a * c - b * s;
                    sumIm += a * s + b * c;
                }
                int outIdx = 2 * (n1 + N1 * k2);
                B[outIdx] = sumRe;
                B[outIdx + 1] = sumIm;
            }
        }

        for (int n1 = 0; n1 < N1; n1++) {
            for (int k2 = 0; k2 < N2; k2++) {
                int idx = 2 * (n1 + N1 * k2);
                float re = B[idx];
                float im = B[idx + 1];
                float c = TW_COS[n1][k2];
                float s = TW_SIN[n1][k2];
                B[idx] = re * c - im * s;
                B[idx + 1] = re * s + im * c;
            }
        }

        for (int k2 = 0; k2 < N2; k2++) {
            for (int k1 = 0; k1 < N1; k1++) {
                float sumRe = 0f, sumIm = 0f;
                for (int n1 = 0; n1 < N1; n1++) {
                    int idx = 2 * (n1 + N1 * k2);
                    float a = B[idx];
                    float b = B[idx + 1];
                    float c = COS_16[k1][n1];
                    float s = SIN_16[k1][n1];
                    sumRe += a * c - b * s;
                    sumIm += a * s + b * c;
                }
                int outIdx = 2 * (k1 * N2 + k2);
                output[outIdx] = sumRe;
                output[outIdx + 1] = sumIm;
            }
        }
    }

    private static class WhisperVocab {
        int tokenEOT = 50256;
        int tokenSOT = 50257;
        int tokenPREV = 50360;
        int tokenSOLM = 50361;
        int tokenNOT = 50362;
        int tokenBEG = 50363;

        final int tokenTRANSLATE = 50358;
        final int tokenTRANSCRIBE = 50359;

        final int nVocabEnglish = 51864;
        final int nVocabMultilingual = 51865;
        Map<Integer, String> tokenToWord = new HashMap<>();
    }

    private static class WhisperFilter {
        int nMel = 0;
        int nFft = 0;
        float[] data;
    }

    private static class WhisperMel {
        int nLen = 0;
        int nMel = 0;
        float[] data;
    }

    private static class InputLang {
        String name;
        String code;
        long id;

        private InputLang(String name, String code, long id) {
            this.name = name;
            this.code = code;
            this.id = id;
        }

        private ArrayList<InputLang> getLangList() {
            ArrayList<InputLang> inputLangList = new ArrayList<>();
            inputLangList.add(new InputLang("English", "en", 50259));
            inputLangList.add(new InputLang("Spanish", "es", 50262));
            inputLangList.add(new InputLang("Hindi", "hi", 50276));
            inputLangList.add(new InputLang("Telugu", "te", 50299));
            return inputLangList;
        }
    }
}
