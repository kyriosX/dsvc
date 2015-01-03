package com.kyrioslab.dsvc.node.client;

/**
 * Created by Ivan Kirilyuk on 28.12.14.
 *
 */

import akka.actor.ActorRef;
import akka.actor.UntypedActor;
import akka.dispatch.OnComplete;
import akka.dispatch.OnFailure;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.routing.FromConfig;
import com.google.common.collect.Lists;
import com.kyrioslab.dsvc.node.messages.ClusterMessage;
import com.kyrioslab.dsvc.node.messages.LocalMessage;
import com.kyrioslab.dsvc.node.util.FFMPEGService;
import com.kyrioslab.jffmpegw.attributes.AudioAttributes;
import com.kyrioslab.jffmpegw.attributes.CommonAttributes;
import com.kyrioslab.jffmpegw.attributes.VideoAttributes;
import org.apache.commons.io.FileUtils;
import scala.concurrent.Future;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

import static akka.dispatch.Futures.future;

public class Client extends UntypedActor {

    private LoggingAdapter log = Logging.getLogger(getContext().system(), this);

    /**
     * Count of parts sender threads.
     */
    private static final int SENDER_COUNT = 4;

    /**
     * Encoder router.
     */
    private ActorRef encoder = getContext().actorOf(FromConfig.getInstance().props(),
            "videoEncoderRouter");

    /**
     * Used for tracking parts.
     */
    private Map<String, List<String>> partsTrackMap = new ConcurrentHashMap<>();

    private final FFMPEGService ffmpegService;

    public Client(FFMPEGService ffmpegService) {
        this.ffmpegService = ffmpegService;
    }

    @Override
    public void onReceive(Object message) {

        //prepare video file for encoding and send parts to encoders
        if (message instanceof LocalMessage.EncodeVideoMessage) {
            final LocalMessage.EncodeVideoMessage encodeMessage = (LocalMessage.EncodeVideoMessage) message;

            final String vFormat = encodeMessage.getCommonAttributes().getFormat();
            final String vPath = encodeMessage.getPathToVideo();

            //start splitting asynchronously
            Future<List<File>> splitFuture = future(new Callable<List<File>>() {
                public List<File> call() throws Exception {
                    return ffmpegService.splitVideo(vFormat, vPath);
                }
            }, getContext().dispatcher());

            //send parts when split finished
            splitFuture.onComplete(new OnComplete<List<File>>() {
                @Override
                public void onComplete(Throwable failure, List<File> partList) throws Throwable {
                    if (failure != null) {
                        log.error("Split failed: ", failure.getMessage());
                        return;
                    } else if (partList == null) {
                        log.error("Split failed: no parts");
                        return;
                    }
                    sendParts(partList, encodeMessage.getCommonAttributes(),
                            encodeMessage.getAudioAttributes(),
                            encodeMessage.getVideoAttributes());
                }
            }, getContext().dispatcher());

            //received encoded part
        } else if (message instanceof ClusterMessage.EncodeResultPartMessage) {
            final ClusterMessage.EncodeResultPartMessage encoded =
                    (ClusterMessage.EncodeResultPartMessage) message;

            //if part message valid
            if (encoded.getPartId() != null
                    && encoded.getPayload() != null
                    && encoded.getFormat() != null) {

                log.info("Received encode part result message: {}", encoded.getPartId());
                final String batchId = ffmpegService.batchIdFromPartId(encoded.getPartId());
                List<String> partList = partsTrackMap.get(batchId);

                //untrack and save part
                if (partList.remove(encoded.getPartId())) {
                    partsTrackMap.put(batchId, partList);
                    File resDir = ffmpegService.getReceiveDir(batchId);
                    String partId = encoded.getPartId();
                    String partName = ffmpegService.partNameFromPartId(partId, encoded.getFormat());
                    try {
                        File resPart = Paths.get(resDir.getAbsolutePath(), partName).toFile();
                        FileUtils.writeByteArrayToFile(resPart, encoded.getPayload());
                    } catch (IOException e) {
                        log.error("IOException occurred while saving encoded part: {}", partId);
                    }

                    //merge results if no parts pending
                    if (partList.isEmpty()) {
                        partsTrackMap.remove(batchId);
                        Future<File> mergeFuture = future(new Callable<File>() {
                            @Override
                            public File call() throws Exception {
                                return ffmpegService.merge(batchId, "result." + encoded.getFormat());
                            }
                        }, getContext().dispatcher());

                        mergeFuture.onComplete(new OnComplete<File>() {
                            @Override
                            public void onComplete(Throwable failure, File success) throws Throwable {
                                if (failure != null) {
                                    log.error("Merge failure, batchId {}, error msg: {}", batchId,
                                            failure.getMessage());
                                } else {
                                    log.info("Video successfully encoded, result video: {}",
                                            success.getAbsoluteFile());
                                    getSender().tell(success.getAbsolutePath(), getSelf());
                                }
                            }
                        }, getContext().dispatcher());
                    }
                } else {
                    log.error("Received unknown part: {}", encoded.getPartId());
                }
            } else {
                log.error("Receive invalid result message from encoder: {} ", getSender());
            }
        } else if (message instanceof ClusterMessage.EncodePartFailed) {
            final ClusterMessage.EncodePartFailed failedMsg = (ClusterMessage.EncodePartFailed) message;

            log.warning("Received failed part message: {}. Sending part to different encoder.", failedMsg);
            String partId = failedMsg.getPartId();
            File partFile = ffmpegService.getPartFile(partId);
            if (partFile.exists()) {
                try {
                    byte[] payload = FileUtils.readFileToByteArray(partFile);
                    ClusterMessage.EncodeVideoPartMessage msg = new ClusterMessage.EncodeVideoPartMessage(partId,
                            payload,
                            failedMsg.getCommonAttributes(),
                            failedMsg.getAudioAttributes(),
                            failedMsg.getVideoAttributes());
                    sendPart(msg, ffmpegService.batchIdFromPartId(partId));
                } catch (IOException e) {
                    log.error("IOException while reading part: {}", partFile.getAbsolutePath());
                }
            } else {
                log.error("Resending failed. Part {} does not exists.", partFile.getAbsolutePath());
            }
        } else {
            unhandled(message);
        }
    }

    /**
     * Send video parts to encoders actors.
     * Using SENDER_COUNT concurrent threads.
     *
     * @param partList list of part files
     * @param ca       common attributes
     * @param aa       audio attributes
     * @param va       video attributes
     * @throws IOException
     */
    protected void sendParts(List<File> partList,
                             final CommonAttributes ca,
                             final AudioAttributes aa,
                             final VideoAttributes va) throws IOException {

        //batch id - is id for all parts of the video
        final String batchId = partList.get(0).getParentFile().getName();

        //sending parts concurrently by butches
        for (final List<File> batch : Lists.partition(partList, SENDER_COUNT)) {
            future(new Callable<Object>() {
                @Override
                public Object call(){
                    for (File part : batch) {
                        String partId = ffmpegService.getPartId(part);

                        //TODO: memory leak here
                        try {
                            byte[] payload = FileUtils.readFileToByteArray(part);
                            ClusterMessage.EncodeVideoPartMessage encodeMsg =
                                    new ClusterMessage.EncodeVideoPartMessage(partId, payload, ca, aa, va);
                            sendPart(encodeMsg, batchId);
                        } catch (IOException e) {
                            log.error("Failed sending part: {}", partId);
                            //TODO: sen message to part tracker about failed part
                        }
                    }
                    return null;
                }
            }, getContext().dispatcher()).onFailure(new OnFailure() {
                @Override
                public void onFailure(Throwable failure) throws Throwable {
                    if (failure != null) {
                        log.error("Failure while sending batch: {}, ",batchId, failure.getMessage());
                    }
                }
            }, getContext().dispatcher());
        }
    }

    protected void sendPart(ClusterMessage.EncodeVideoPartMessage vPartMsg, String batchId) {

        //add part to tracking map
        List<String> batch = partsTrackMap.get(batchId);
        if (batch == null) {
            batch = new LinkedList<>();
        }
        batch.add(vPartMsg.getPartId());
        partsTrackMap.put(batchId, batch);

        //send part
        encoder.tell(vPartMsg, getSelf());
    }
}
