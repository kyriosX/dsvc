package com.kyrioslab.dsvc.node.encoder;

/**
 * Created by Ivan Kirilyuk on 28.12.14.
 */

import akka.actor.UntypedActor;
import akka.dispatch.Mapper;
import akka.dispatch.OnComplete;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import com.kyrioslab.dsvc.node.client.Client;
import com.kyrioslab.dsvc.node.messages.ClusterMessage;
import com.kyrioslab.jffmpegw.attributes.AudioAttributes;
import com.kyrioslab.jffmpegw.attributes.CommonAttributes;
import com.kyrioslab.jffmpegw.attributes.VideoAttributes;
import com.kyrioslab.jffmpegw.command.BuilderException;
import com.kyrioslab.jffmpegw.command.Command;
import com.kyrioslab.jffmpegw.command.EncodeCommandBuilder;
import org.apache.commons.io.FileUtils;
import scala.concurrent.Future;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.concurrent.Callable;

import static akka.dispatch.Futures.future;
import static akka.pattern.Patterns.pipe;


public class Encoder extends UntypedActor {

    private LoggingAdapter log = Logging.getLogger(getContext().system(), this);

    /**
     * Encoded part prefix
     */
    public static final String ENCODE_RESULT = "r-";

    /**
     * Temporary dir for encoded parts
     */
    public static final String TMP_DIR = System.getProperty("java.io.tmpdir");

    public static final String FFMPEG_LOCATION = Encoder.class.getResource("/ffmpeg").getPath();

    @Override
    public void onReceive(Object message) {
        if (message instanceof ClusterMessage.EncodeVideoPartMessage) {

            log.info("Received part: {}", message);

            final ClusterMessage.EncodeVideoPartMessage msg =
                    (ClusterMessage.EncodeVideoPartMessage) message;
            final File src = Paths.get(TMP_DIR, msg.getPartId()).toFile();

            log.debug("Saving part to file: {}", src.getAbsolutePath());
            try {
                FileUtils.writeByteArrayToFile(src, msg.getPayload());
            } catch (IOException e) {
                log.error("Error occurred while writing part to file: {}", src.getAbsolutePath());
                ClusterMessage.EncodePartFailed failedMsg =
                        new ClusterMessage.EncodePartFailed("IOException while saving part to file",
                                msg.getPartId(),
                                msg.getCommonAttributes(),
                                msg.getAudioAttributes(),
                                msg.getVideoAttributes());
                getSelf().tell(failedMsg, getSelf());
                return;
            }

            //start encoding process
            Future<File> encodeFuture = future(new Callable<File>() {
                public File call() throws Exception{
                    try {
                        return encode(src,
                                msg.getCommonAttributes(),
                                msg.getAudioAttributes(),
                                msg.getVideoAttributes());
                    } catch (BuilderException e) {
                        e.printStackTrace();
                        return null;
                    }
                }
            }, getContext().dispatcher());

            encodeFuture.onComplete(new OnComplete<File>() {
                @Override
                public void onComplete(Throwable failure, File success) throws Throwable {
                    if (failure != null) {
                        ClusterMessage.EncodePartFailed failedMsg =
                                new ClusterMessage.EncodePartFailed("Exception while encoding part",
                                        msg.getPartId(),
                                        msg.getCommonAttributes(),
                                        msg.getAudioAttributes(),
                                        msg.getVideoAttributes());
                        getSender().tell(failedMsg, getSelf());
                    } else {
                        byte[] payload = FileUtils.readFileToByteArray(success);
                        ClusterMessage.EncodeResultPartMessage resultMsg =
                                new  ClusterMessage.EncodeResultPartMessage(msg.getPartId(),
                                        payload,
                                        msg.getCommonAttributes().getFormat());
                    }
                }
            }, getContext().dispatcher());
        } else {
            unhandled(message);
        }
    }

    //TODO: refactor
    protected File encode(File src,
                          CommonAttributes ca,
                          AudioAttributes aa,
                          VideoAttributes va) throws BuilderException {

        ca.setInputFile(src.getAbsolutePath());
        Command command = new EncodeCommandBuilder(FFMPEG_LOCATION,
                ca,
                va,
                aa).build();
        String resultFileName = ENCODE_RESULT + src.getName().substring(0, src.getName().indexOf(".")) + "." + ca.getFormat();
        command.addAttribute(resultFileName);
        try {
            Process p = new ProcessBuilder(command.getCommand())
                    .directory(new File(TMP_DIR)).start();
            int code = p.waitFor();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return Paths.get(TMP_DIR, resultFileName).toFile();
    }
}
