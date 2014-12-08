package argus.util;

import argus.Context;
import org.apache.commons.io.FileDeleteStrategy;

import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.text.DecimalFormat;

/**
 * @author Eduardo Duarte (<a href="mailto:eduardo.miguel.duarte@gmail.com">eduardo.miguel.duarte@gmail.com</a>)
 * @version 1.0
 */
public class Constants {

    public static final String PROJECT_DIR = System.getProperty("user.dir")
            + File.separator
            + "src"
            + File.separator
            + "main";

    public static final File INSTALL_DIR = new File(
            Context.class.getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .getPath())
            .getAbsoluteFile()
            .getParentFile();

    public static final File LANGUAGE_PROFILES_DIR =
            new File(INSTALL_DIR, "lang-profiles");

    public static final File READER_CLASSES_DIR =
            new File(INSTALL_DIR, "readers");

    public static final File STEMMER_CLASSES_DIR =
            new File(INSTALL_DIR, "stemmers");

    public static final File STOPWORDS_DIR =
            new File(INSTALL_DIR, "stopwords");

    public static final File PARSER_DIR =
            new File(INSTALL_DIR, "parser");

    private static final SecureRandom random = new SecureRandom();

    private static final char[] hexArray = "0123456789abcdef".toCharArray();


    /**
     * Generates and returns a 128-bit random hash.
     */
    public static byte[] generateRandomBytes() {
        byte[] bytes = new byte[8];
        random.nextBytes(bytes);
        return bytes;
    }


    /**
     * Converts a byte-hash into hexadecimal format.
     */
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }


    public static void deleteFile(File fileToDelete) {
        try {
            FileDeleteStrategy.FORCE.delete(fileToDelete);
        } catch (IOException e) {
            fileToDelete.delete();
        }
    }


    public static long folderSize(File directory) {
        long length = 0;
        File[] subFiles = directory.listFiles();
        if (subFiles != null) {
            for (File f : subFiles) {
                length += f.isFile() ? f.length() : folderSize(f);
            }
        }
        return length;
    }


    public static String fileSizeToString(long size) {
        if (size <= 0) {
            return "0 kb";
        }

        final String[] units = new String[]{"bytes", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }


    public static long difference(long n1, long n2) {
        long result = n1 - n2;
        return result >= 0 ? result : -result;
    }


    public static int difference(int n1, int n2) {
        int result = n1 - n2;
        return result >= 0 ? result : -result;
    }
}
