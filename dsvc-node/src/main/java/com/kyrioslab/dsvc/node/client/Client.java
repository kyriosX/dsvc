package com.kyrioslab.dsvc.node.client;

/**
 * Created by Ivan Kirilyuk on 28.12.14.
 */

import akka.actor.ActorRef;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.routing.FromConfig;
import com.kyrioslab.dsvc.node.util.VideoFileComaprator;
import com.kyrioslab.dsvc.node.messages.ClusterMessage;
import com.kyrioslab.dsvc.node.messages.LocalMessage;
import com.kyrioslab.jffmpegw.attributes.AudioAttributes;
import com.kyrioslab.jffmpegw.attributes.CommonAttributes;
import com.kyrioslab.jffmpegw.attributes.VideoAttributes;
import com.kyrioslab.jffmpegw.command.MergeCommand;
import com.kyrioslab.jffmpegw.command.SplitCommand;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;

public class Client extends UntypedActor {

    public static final String DELIMETER_ID = "--@@@--";
    public static final String RECEIVE_DIR_PREFIX = "res-";
    public static final String LIST_FILE_NAME = "list.txt";
    private static final String SPLIT_OUTPUT_FORMAT = "%d";

    private static final int SEGMENT_TIME = 30;
    public static final String TMP_DIR = System.getProperty("java.io.tmpdir");
    public static String FFMPEGLocation = Client.class.getResource("/ffmpeg").getPath();

    private LoggingAdapter log = Logging.getLogger(getContext().system(), this);
    private ActorRef encoder = getContext().actorOf(FromConfig.getInstance().props(),
            "videoEncoderRouter");
    private Map<String, List<String>> partsTrackMap = new HashMap<String, List<String>>();

    @Override
    public void onReceive(Object message) {
        if (message instanceof LocalMessage.EncodeVideoMessage) {
            LocalMessage.EncodeVideoMessage encodeMessage = (LocalMessage.EncodeVideoMessage) message;
            try {
                List<File> partList = splitVideo(encodeMessage.getCommonAttributes().getFormat(), encodeMessage.getPathToVideo());
                sendParts(partList, encodeMessage.getCommonAttributes(),
                        encodeMessage.getAudioAttributes(),
                        encodeMessage.getVideoAttributes());
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else if (message instanceof ClusterMessage.EncodeResultPartMessage) {
            ClusterMessage.EncodeResultPartMessage encoded =
                    (ClusterMessage.EncodeResultPartMessage) message;
            if (!(encoded.getPartId() == null && encoded.getPayload() == null)) {
                String batchId = batchIdFromPartId(encoded.getPartId());
                List<String> partList = partsTrackMap.get(batchId);
                if (partList.contains(encoded.getPartId())) {
                    partList.remove(encoded.getPartId());
                    File recvDir = getReceiveDir(batchId);
                    String partId = encoded.getPartId();
                    String partName = partId.substring(0, partId.indexOf(".") + 1) + encoded.getFormat();
                    try {
                        File resPart = Paths.get(recvDir.getAbsolutePath(),
                                partName.substring(partName.indexOf(DELIMETER_ID) + DELIMETER_ID.length())).toFile();
                        FileUtils.writeByteArrayToFile(resPart, encoded.getPayload());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else {
                    log.error("Received unknown part for batch: " + batchId);
                }
                if (partList.isEmpty()) {
                    merge(getReceiveDir(batchId), encoded.getFormat());
                }
            }
        } else {
            unhandled(message);
        }
    }

    /**
     * Splits source video file into parts. Creates output directory
     * for splited parts and directory for reseiving results.
     *
     * @param path absolute video path
     * @return list of splitted parts
     * @throws IOException
     */
    protected List<File> splitVideo(String format, String path) throws IOException {
        SplitCommand command = new SplitCommand(FFMPEGLocation, path, SEGMENT_TIME, 0);
        command.addAttribute("%d." + format);
        String batchUUID = UUID.randomUUID().toString();
        File outputDir = Paths.get(TMP_DIR, batchUUID).toFile();
        File receiveDir = getReceiveDir(batchUUID);

        boolean createdOutput = outputDir.mkdir();
        boolean cteatedReceiving = receiveDir.mkdir();

        if (!(createdOutput && cteatedReceiving)) {
            throw new IOException("Cannot create service directories");
        }

        Process p = new ProcessBuilder(command.getCommand()).directory(outputDir).start();
        List<File> partList = new ArrayList<File>();
        try {
            int exCode = p.waitFor();
            if (exCode != 0) {
                log.error("Error while splitting video file: " + path);
            }
            File[] parts = outputDir.listFiles();
            if (parts != null) {
                partList = Arrays.asList(parts);
            } else {
                log.error("No parts splitted");
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return partList;
    }

    protected void sendParts(List<File> partList, CommonAttributes ca,
                             AudioAttributes aa,
                             VideoAttributes va) throws IOException {
        byte[] payload;
        String batchId = partList.get(0).getParentFile().getName();
        LinkedList<String> batch = new LinkedList<String>();
        for (File part : partList) {
            String partId = getPartId(part);
            payload = FileUtils.readFileToByteArray(part);

            batch.add(partId);
            ClusterMessage.EncodeVideoPartMessage encodeMsg =
                    new ClusterMessage.EncodeVideoPartMessage(partId, payload, ca, aa, va);
            encoder.tell(encodeMsg, getSelf());
        }
        partsTrackMap.put(batchId, batch);
    }

    protected File merge(File recvDir, String format) {
        //form parts list
        File[] encodedList = recvDir.listFiles();
        Arrays.sort(encodedList, new VideoFileComaprator());
        File list = Paths.get(recvDir.getAbsolutePath(), LIST_FILE_NAME).toFile();
        try {
            list.createNewFile();
            FileWriter fw = new FileWriter(list);
            if (encodedList != null) {
                for (File e : encodedList) {
                    fw.write("file '" + e.getAbsolutePath() + "'\n"); //XXX: endline
                }
                fw.flush();
                fw.close();
            } else {
                log.error("Receiving directory is empty");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        MergeCommand command = new MergeCommand(FFMPEGLocation, list.getName(), "output." + format);
        try {
            Process p = new ProcessBuilder(command.getCommand()).directory(recvDir).start();
            int res = p.waitFor();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return Paths.get(recvDir.getAbsolutePath(), "output." + format).toFile();
    }

    public String getPartId(File part) {
        return part.getParentFile().getName() + DELIMETER_ID + part.getName();
    }

    public String batchIdFromPartId(String partId) {
        return partId.substring(0, partId.indexOf(DELIMETER_ID));
    }

    private File getReceiveDir(String batchUUID) {
        return Paths.get(TMP_DIR, RECEIVE_DIR_PREFIX + batchUUID).toFile();
    }
}
