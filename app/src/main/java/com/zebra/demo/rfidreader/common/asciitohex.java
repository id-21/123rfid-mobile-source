package com.zebra.demo.rfidreader.common;

import android.util.Log;

/**
 * Created by A24194 on 6/26/2018.
 */

public class asciitohex {
    private static final String TAG = "asciitohex";

    public static String convert(String mData) {
        Log.d(TAG,  "asciitohex input: " + mData);
        if (mData == null) {
            return null;
        }

        // If already hex, return as-is.
        if (hextoascii.isDatainHex(mData)) {
            return mData;
        }

        // Check if we have the raw version cached (with escape sequences)
        String toConvert = hextoascii.getRawVersion(mData);

        String ascii = toConvert;
        if (toConvert.length() >= 2 && toConvert.startsWith("'") && toConvert.endsWith("'")) {
            ascii = toConvert.substring(1, toConvert.length() - 1);
        }

        StringBuilder builder = new StringBuilder(ascii.length() * 2);

        for (int i = 0; i < ascii.length(); i++) {
            char ch = ascii.charAt(i);

            // Parse escaped non-printable byte: \xHH
            if (ch == '\\' && i + 3 < ascii.length() && ascii.charAt(i + 1) == 'x'
                    && isHexChar(ascii.charAt(i + 2)) && isHexChar(ascii.charAt(i + 3))) {
                builder.append(Character.toLowerCase(ascii.charAt(i + 2)));
                builder.append(Character.toLowerCase(ascii.charAt(i + 3)));
                i += 3;
                continue;
            }

            int value = toHexByteValue(ch);
            if (value < 0) {
                return mData;
            }
            builder.append(String.format("%02x", value));
        }
        Log.d(TAG,  "asciitohex Output: " + builder.toString());
        return builder.toString();
    }

    /**
     * Reverse mapping for hextoascii.hex2ascii():
     * - ASCII printable chars map to their byte value directly.
     * - Extended chars map using EXTENDED_ASCII_CHAR table index + 0x7F.
     * - Space maps to 0x20 (hextoascii emits space for non-printable/unmapped bytes).
     */
    private static int toHexByteValue(char ch) {
        if (ch == ' ') {
            return -1;
        }

        // ASCII printable range used in hextoascii
        if (ch > 0x20 && ch <= 0x7F) {
            return (int) ch;
        }

        // Reverse lookup in extended table
        for (int i = 0; i < hextoascii.EXTENDED_ASCII_CHAR.length; i++) {
            if (hextoascii.EXTENDED_ASCII_CHAR[i] == ch) {
                return i + 0x7F;
            }
        }

        return -1;
    }

    private static boolean isHexChar(char ch) {
        return (ch >= '0' && ch <= '9')
                || (ch >= 'a' && ch <= 'f')
                || (ch >= 'A' && ch <= 'F');
    }
}