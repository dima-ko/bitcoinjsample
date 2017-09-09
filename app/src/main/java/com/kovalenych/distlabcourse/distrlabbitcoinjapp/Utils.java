package com.kovalenych.distlabcourse.distrlabbitcoinjapp;

import org.bitcoinj.core.Address;

import java.util.List;

/**
 * Created by Dima Kovalenko on 9/10/17.
 */

public class Utils {

    private final static char[] hexArray = "0123456789ABCDEF".toCharArray();

    static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }

    static String concatWithCommas(List<Address> addresses) {
        StringBuilder result = new StringBuilder();
        for (Address address : addresses) {
            result.append(address.toBase58());
            result.append(",");
        }
        return result.length() > 0 ? result.substring(0, result.length() - 1) : "";
    }

    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

}
