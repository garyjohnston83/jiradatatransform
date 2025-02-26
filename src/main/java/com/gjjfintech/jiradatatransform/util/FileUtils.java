package com.gjjfintech.jiradatatransform.util;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;

public class FileUtils {

    /**
     * Determines the CSV file path based on the folder, latestFile flag, and provided filename.
     *
     * @param folder      the data folder path.
     * @param latestFile  if true, select the file with the latest timestamp (filenames ending with yyyymmddhhmmss).
     * @param filename    if latestFile is false, the filename to use.
     * @return the full file path.
     */
    public static String determineCsvFilePath(String folder, boolean latestFile, String filename) {
        File dir = new File(folder);
        if (!dir.exists() || !dir.isDirectory()) {
            throw new IllegalArgumentException("Data folder " + folder + " is not a valid directory.");
        }
        if (latestFile) {
            // List files in the folder that match the pattern: any name ending with 14 digits.
            File[] files = dir.listFiles((d, name) -> name.matches(".*\\d{14}\\.csv$"));
            if (files == null || files.length == 0) {
                throw new IllegalStateException("No files with timestamp found in folder " + folder);
            }
            // Sort files by timestamp (extracted from filename) and pick the latest.
            Arrays.sort(files, Comparator.comparingLong(f -> extractTimestamp(f.getName())));
            return files[files.length - 1].getAbsolutePath();
        } else {
            // Use the provided filename.
            File file = new File(dir, filename);
            if (!file.exists() || !file.isFile()) {
                throw new IllegalArgumentException("File " + file.getAbsolutePath() + " does not exist.");
            }
            return file.getAbsolutePath();
        }
    }

    /**
     * Extracts a timestamp (as long) from a filename.
     * Assumes the filename ends with yyyymmddhhmmss.
     *
     * @param filename the filename.
     * @return the timestamp as a long value.
     */
    private static long extractTimestamp(String filename) {
        // Extract the last 14 characters from the filename.
        String ts = filename.substring(filename.length() - 14);
        // Parse it as a number.
        try {
            return Long.parseLong(ts);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
