package com.kyrioslab.dsvc.node.client;

/**
 * Created by Ivan Kirilyuk on 28.12.14.
 *
 */

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.cluster.Cluster;
import akka.cluster.ClusterEvent;
import akka.dispatch.OnComplete;
import akka.dispatch.OnFailure;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.routing.FromConfig;
import com.google.common.collect.Lists;
import com.kyrioslab.dsvc.node.messages.ClusterMessage;
import com.kyrioslab.dsvc.node.messages.LocalMessage;
import com.kyrioslab.dsvc.node.util.FFMPEGService;
import com.kyrioslab.dsvc.node.util.PartTrackService;
import com.kyrioslab.jffmpegw.command.EncodeCommand;
import org.apache.commons.io.FileUtils;
import scala.concurrent.Future;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

    /**
     * Tracks client for sending result back.
     */
    private Map<String, ActorRef> clientTrackMap = new ConcurrentHashMap<>();

    /**
     * Service provides core methods.
     */
    private final FFMPEGService ffmpegService;

    /**
     * Service for handling parts timeouts
     */
    private final ActorRef partTrackService;

    /**
     * Last received cluster metrics. Webgui gets it with
     * cluster status.
     */
    private ClusterEvent.ClusterMetricsChanged lastMetrics;

    public Client(FFMPEGService ffmpegService) {
        this.ffmpegService = ffmpegService;
        partTrackService = getContext().system().actorOf(Props.create(PartTrackService.class, getSelf()));
    }

    //subscribe to ClusterMetricsChanged
    @Override
    public void preStart() {
        Cluster.get(getContext().system())
                .subscribe(getSelf(), ClusterEvent.ClusterMetricsChanged.class);
    }

    //re-subscribe when restart
    @Override
    public void postStop() {
        Cluster.get(getContext().system())
                .unsubscribe(getSelf());
    }

    @Override
    public void onReceive(Object message) {

        //prepare video file for encoding and send parts to encoders
        if (message instanceof LocalMessage.EncodeVideoMessage) {

            final LocalMessage.EncodeVideoMessage encodeMessage = (LocalMessage.EncodeVideoMessage) message;

            log.info("Received file for encoding: {}, command: {}", encodeMessage.getPathToVideo(),
                    encodeMessage.getCommand().getCommand());

            final String vFormat = encodeMessage.getCommand().getInputFormat();
            final String vPath = encodeMessage.getPathToVideo();
            final String batchUUID = UUID.randomUUID().toString();

            //track client
            clientTrackMap.put(batchUUID, getSender());

            //start splitting asynchronously
            Future<List<File>> splitFuture = future(new Callable<List<File>>() {
                public List<File> call() throws Exception {
                    return ffmpegService.splitVideo(vFormat, vPath, batchUUID,
                            encodeMessage.getDuration());
                }
            }, getContext().dispatcher());

            //send parts when split finished
            splitFuture.onComplete(new OnComplete<List<File>>() {
                @Override
                public void onComplete(Throwable failure, List<File> partList) throws Throwable {
                    if (failure != null) {
                        log.error("Split failed: ", failure.toString());
                        return;
                    } else if (partList == null) {
                        log.error("Split failed: no parts");
                        return;
                    }
                    sendParts(partList, encodeMessage.getCommand());
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
                final String batchId = FFMPEGService.batchIdFromPartId(encoded.getPartId());

                //untrack part from tracker
                partTrackService.tell(new LocalMessage.UntrackPartMessage(encoded.getPartId())
                        , getSelf());

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

                    //send progress message
                    clientTrackMap.get(batchId)
                            .tell(new LocalMessage.ProgressMessage(), getSelf());

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
                                    log.info("Video successfully encoded, sending result video: {}",
                                            success.getAbsoluteFile());
                                    clientTrackMap.get(batchId).tell(
                                            new LocalMessage.EncodeResult(success.getAbsolutePath()),
                                            getSelf());
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

            //FAILFAST FOR NOW
            final String partId = failedMsg.getPartId();
            final String batchId = FFMPEGService.batchIdFromPartId(partId);

            log.info("Removing all parts from track (as failfast) with batch id: "
                    + batchId);

            partTrackService.tell(new LocalMessage.UntrackPartMessage(partId), getSelf());
            partsTrackMap.remove(batchId);
            ActorRef client = clientTrackMap.remove(batchId);

            if (client != null) {
                client.tell(
                        new LocalMessage.EncodeJobFailedMessage(failedMsg.getReason(),
                                failedMsg.getCommand()),
                        getSelf());
            } else {
                log.warning("Unable to get registered client gui, mb already removed?");
            }

//            log.warning("Received failed part message: {}. Sending part to different encoder.", failedMsg);
//            String partId = failedMsg.getPartId();
//            File partFile = ffmpegService.getPartFile(partId);
//            if (partFile.exists()) {
//                try {
//                    byte[] payload = FileUtils.readFileToByteArray(partFile);
//                    ClusterMessage.EncodeVideoPartMessage msg = new ClusterMessage.EncodeVideoPartMessage(partId,
//                            payload,
//                            failedMsg.getCommand());
//                    //reset part
//                    partTrackService.tell(new LocalMessage.ResetPartTimeMessage(partId),
//                            getSelf());
//
//                    sendPart(msg, FFMPEGService.batchIdFromPartId(partId));
//                } catch (IOException e) {
//                    log.error("IOException while reading part: {}", partFile.getAbsolutePath());
//                    partTrackService.tell(new LocalMessage.UntrackPartMessage(partId), getSelf());
//                }
//            } else {
//                log.error("Resending failed. Part {} does not exists.", partFile.getAbsolutePath());
//                partTrackService.tell(new LocalMessage.UntrackPartMessage(partId), getSelf());
//            }
        } else if (message instanceof LocalMessage.ClusterStatusRequestMessage) {
            ClusterEvent.CurrentClusterState currentClusterState = getClusterState();

            getSender().tell(new LocalMessage.ClusterStatusResponceMessage(
                    currentClusterState,
                    lastMetrics
            ), getSelf());
        } else if (message instanceof ClusterEvent.ClusterMetricsChanged) {
            lastMetrics = (ClusterEvent.ClusterMetricsChanged) message;
        } else {
            unhandled(message);
        }
    }

    /**
     * Send video parts to encoders actors.
     * Using SENDER_COUNT concurrent threads.
     *
     * @param partList list of part files
     * @throws IOException
     */
    protected void sendParts(List<File> partList,
                             final EncodeCommand encodeCommand) throws IOException {

        //batch id - is id for all parts of the video
        final String batchId = partList.get(0).getParentFile().getName();

        log.info("Start sending parts concurrently, batchId: {}", batchId);

        //sending parts concurrently by butches
        for (final List<File> batch : Lists.partition(partList, SENDER_COUNT)) {
            future(new Callable<Object>() {
                @Override
                public Object call() {
                    for (File part : batch) {
                        String partId = ffmpegService.getPartId(part);

                        //TODO: memory leak here
                        try {
                            byte[] payload = FileUtils.readFileToByteArray(part);
                            ClusterMessage.EncodeVideoPartMessage encodeMsg =
                                    new ClusterMessage.EncodeVideoPartMessage(partId, payload, encodeCommand);
                            //add part to tracker
                            partTrackService.tell(
                                    new LocalMessage.PlaceOnTrackMessage(partId, encodeCommand),
                                    getSelf());

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
                        log.error("Failure while sending batch: {}, ", batchId, failure.getMessage());
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

        log.info("Part sent: {}", vPartMsg.getPartId());
    }

    private ClusterEvent.CurrentClusterState getClusterState() {
        return Cluster.get(getContext().system()).state();
    }
}
