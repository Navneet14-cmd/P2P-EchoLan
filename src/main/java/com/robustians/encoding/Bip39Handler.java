package com.robustians.encoding;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.io.*;
import java.util.stream.Collectors;

public class Bip39Handler {

    private static final int WORD_BITS = 11;
    private static final int WORD_MASK = (1 << WORD_BITS) - 1;

    private static final String WORDLIST_FILE = "bip39_2048.txt";

    private final List<String> wordList;
    private final Map<String, Integer> wordIndex;

    public Bip39Handler() throws IOException {
        InputStream is = getClass().getClassLoader().getResourceAsStream(WORDLIST_FILE);

        if (is == null) {
            throw new FileNotFoundException("Resource not found: " + WORDLIST_FILE);
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            wordList = reader.lines()
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        }

        if (wordList.size() != 2048) {
            throw new IllegalStateException("Wordlist must contain exactly 2048 words, found: " + wordList.size());
        }

        wordIndex = new HashMap<>();
        for (int i = 0; i < wordList.size(); i++) {
            wordIndex.put(wordList.get(i).toLowerCase(), i);
        }
    }

    // ----------------------------
    // IP -> Words
    // ----------------------------
    public String ipToWords(String ip) {
        int ipInt = ipToInt(ip);

        long value = ipInt & 0xFFFFFFFFL;

        // pad to 33 bits
        value <<= 1;

        StringBuilder result = new StringBuilder();

        for (int i = 2; i >= 0; i--) {
            int index = (int) ((value >> (i * WORD_BITS)) & WORD_MASK);
            result.append(wordList.get(index));
            if (i > 0)
                result.append(" ");
        }

        return result.toString();
    }

    // ----------------------------
    // Words -> IP
    // ----------------------------
    public String wordsToIp(String words) throws IllegalArgumentException {
        String[] parts = words.trim().toLowerCase().split("\\s+");

        if (parts.length != 3) {
            throw new IllegalArgumentException("Expected exactly 3 words.");
        }

        long value = 0;

        for (String word : parts) {
            Integer index = wordIndex.get(word);
            if (index == null) {
                throw new IllegalArgumentException("Invalid word: " + word);
            }

            value = (value << WORD_BITS) | index;
        }

        // remove padding
        value >>= 1;

        return intToIp((int) value);
    }

    // ----------------------------
    // Helpers
    // ----------------------------
    private int ipToInt(String ip) {
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            throw new IllegalArgumentException("Invalid IP");
        }

        int result = 0;
        for (String part : parts) {
            int val = Integer.parseInt(part);
            result = (result << 8) | (val & 0xFF);
        }
        return result;
    }

    private String intToIp(int value) {
        return ((value >> 24) & 0xFF) + "." +
                ((value >> 16) & 0xFF) + "." +
                ((value >> 8) & 0xFF) + "." +
                (value & 0xFF);
    }
}