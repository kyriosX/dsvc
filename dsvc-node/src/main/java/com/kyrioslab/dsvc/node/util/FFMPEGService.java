package com.kyrioslab.dsvc.node.util;

import com.kyrioslab.dsvc.node.MergeProcessException;
import com.kyrioslab.dsvc.node.SplitProcessException;
import com.kyrioslab.jffmpegw.command.Command;
import com.kyrioslab.jffmpegw.command.MergeCommand;
import com.kyrioslab.jffmpegw.command.SplitCommand;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by Ivan Kirilyuk on 28.12.14.
 *
 */

public class FFMPEGService {

    /**
     * Batch id and part number delimiter.
     */
    public static final String DELIMETER_ID = "--@@@--";

    /**
     * Prefix for directory containing received parts.
     */
    public static final String RECEIVE_DIR_PREFIX = "res-";

    /**
     * File name that contains list of parts to merge.
     */
    public static final String LIST_FILE_NAME = "list.txt";

    /**
     * Template for part names, used in split command.
     */
    private static final String SPLIT_OUTPUT_FORMAT = "%d.";

    /**
     * Time of video part. Used in split command.
     */
    private int segmentTime = 30;

    /**
     * Root temporary dir.
     */
    private String tmpDir;

    /**
     * Path to ffmpeg tool.
     */
    private String ffmpeg;

    public FFMPEGService(String ffmpeg, int segmentTime, String tmpDir) {
        this.ffmpeg = ffmpeg;
        this.segmentTime = segmentTime;
        this.tmpDir = tmpDir;
    }

    /**
     * Splits source video file into parts. Creates output directory
     * for parts and directory for encoded results.
     *
     * @param srcPath absolute video path
     * @return list of splitted parts
     * @throws java.io.IOException
     */
    public List<File> splitVideo(String format, String srcPath, String batchUUID) throws IOException, SplitProcessException, InterruptedException {
        SplitCommand command = new SplitCommand(ffmpeg, srcPath, segmentTime, 0);

        //add output format to spit command
        command.addAttribute(SPLIT_OUTPUT_FORMAT + format);

        File outputDir = Paths.get(tmpDir, batchUUID).toFile();
        File receiveDir = getReceiveDir(batchUUID);

        boolean createdOutput = outputDir.mkdir();
        boolean createdReceiving = receiveDir.mkdir();

        if (!(createdOutput && createdReceiving)) {
            throw new IOException("Cannot create service directories");
        }

        try {
            Process p = startProcess(command, outputDir);
            if (p.waitFor() != 0) {
                throw new SplitProcessException(IOUtils.toString(p.getErrorStream()));
            }
        } catch (IOException e) {
            throw new SplitProcessException("Exception while split process. " + e.getMessage());
        }

        File[] parts = outputDir.listFiles();
        return parts == null ?
                new ArrayList<File>() : Arrays.asList(parts);
    }

    /**
     * Merges encoded parts into resulting video.
     *
     * @param batchUUID batch uuid
     * @param fileName  name of resulting video file
     * @return resulting video file
     */
    public File merge(String batchUUID, String fileName) throws IllegalStateException, IOException, MergeProcessException, InterruptedException {

        File resDir = getReceiveDir(batchUUID);
        File[] encodedList = resDir.listFiles();
        if (encodedList == null) {
            throw new IllegalStateException("No files to merge in directory: "
                    + resDir.getAbsolutePath());
        }

        //sort parts to merge them in right order
        Arrays.sort(encodedList, new VideoFileComaprator());

        //form list of video parts for ffmpeg
        File listFile = Paths.get(resDir.getAbsolutePath(), LIST_FILE_NAME).toFile();
        if (!listFile.createNewFile()) {
            throw new IOException("Cannot create file for parts list");
        }
        try (FileWriter fw = new FileWriter(listFile)) {
            for (File e : encodedList) {
                fw.write("file '" + e.getAbsolutePath() + System.lineSeparator());
            }
            fw.flush();
        } catch (IOException e) {
            throw new IOException("Exception while writing list of files for merge", e);
        }

        //merge parts
        MergeCommand command = new MergeCommand(ffmpeg, listFile.getName(), fileName);
        try {
            Process p = startProcess(command, resDir);
            if (p.waitFor() != 0) {
                throw new MergeProcessException(IOUtils.toString(p.getErrorStream()));
            }

            //merge success, remove tmp files
            File srcDir = getSourceDir(batchUUID);
            if (srcDir.exists()) {

                //just ensure we are in tmp dir
                if (srcDir.getParentFile().getPath().equals(tmpDir)) {
                    FileUtils.deleteDirectory(srcDir);
                }
            }
        } catch (IOException e) {
            throw new MergeProcessException("Exception while merge process " + e.getMessage());
        }

        return Paths.get(resDir.getAbsolutePath(), fileName).toFile();
    }

    public File getReceiveDir(String batchUUID) {
        return Paths.get(tmpDir, RECEIVE_DIR_PREFIX + batchUUID).toFile();
    }

    public File getSourceDir(String batchUUID) {
        return Paths.get(tmpDir, batchUUID).toFile();
    }

    public String getPartId(File part) {
        return part.getParentFile().getName() + DELIMETER_ID + part.getName();
    }

    public static String batchIdFromPartId(String partId) {
        return partId.substring(0, partId.indexOf(DELIMETER_ID));
    }

    public String partNameFromPartId(String partId, String resFormat) {
        String nameWithNewFormat = partId.substring(0, partId.indexOf(".") + 1) + resFormat;
        return nameWithNewFormat.substring(nameWithNewFormat.indexOf(DELIMETER_ID) + DELIMETER_ID.length());
    }

    public File getPartFile(String partId) {
        return Paths.get(tmpDir, batchIdFromPartId(partId), partId).toFile();
    }

    private Process startProcess(Command command, File directory) throws IOException {
        return new ProcessBuilder(command.getCommand()).directory(directory).start();
    }
}
