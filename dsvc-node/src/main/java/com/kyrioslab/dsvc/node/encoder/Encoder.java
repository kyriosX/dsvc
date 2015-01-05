package com.kyrioslab.dsvc.node.encoder;

/**
 * Created by Ivan Kirilyuk on 28.12.14.
 *
 */

import akka.actor.ActorRef;
import akka.actor.UntypedActor;
import akka.dispatch.OnComplete;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import com.kyrioslab.dsvc.node.messages.ClusterMessage;
import com.kyrioslab.dsvc.node.EncodeProcessException;
import com.kyrioslab.jffmpegw.attributes.AudioAttributes;
import com.kyrioslab.jffmpegw.attributes.CommonAttributes;
import com.kyrioslab.jffmpegw.attributes.VideoAttributes;
import com.kyrioslab.jffmpegw.command.BuilderException;
import com.kyrioslab.jffmpegw.command.Command;
import com.kyrioslab.jffmpegw.command.EncodeCommandBuilder;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import scala.concurrent.Future;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.concurrent.Callable;

import static akka.dispatch.Futures.future;


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

    /**
     * FFMPEG location
     */
    public static final String FFMPEG_LOCATION = "/home/wizzard/diploma_work/dsvc/ffmpeg/ffmpeg";

    @Override
    public void onReceive(Object message) {
        if (message instanceof ClusterMessage.EncodeVideoPartMessage) {

            final ClusterMessage.EncodeVideoPartMessage msg =
                    (ClusterMessage.EncodeVideoPartMessage) message;

            log.info("Received part: {}", msg.getPartId());

            final File src = Paths.get(TMP_DIR, msg.getPartId()).toFile();

            //TODO: may produse bug
            final ActorRef sender = getSender();

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
                getSender().tell(failedMsg, getSelf());
                return;
            }

            //start encoding process
            Future<File> encodeFuture = future(new Callable<File>() {
                public File call() throws Exception {
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
                public void onComplete(Throwable failure, File encodedFile) throws Throwable {
                    if (failure != null) {
                        ClusterMessage.EncodePartFailed failedMsg =
                                new ClusterMessage.EncodePartFailed("Exception while encoding part",
                                        msg.getPartId(),
                                        msg.getCommonAttributes(),
                                        msg.getAudioAttributes(),
                                        msg.getVideoAttributes());
                        sender.tell(failedMsg, getSelf());
                    } else {
                        byte[] payload = FileUtils.readFileToByteArray(encodedFile);
                        ClusterMessage.EncodeResultPartMessage resultMsg =
                                new  ClusterMessage.EncodeResultPartMessage(msg.getPartId(),
                                        payload,
                                        msg.getCommonAttributes().getFormat());
                        sender.tell(resultMsg, getSelf());

                        //remove tmp files
                        if (!src.delete()) {
                            log.warning("Cannot delete temporary file: {}", src.getAbsolutePath());
                        }
                        if (!encodedFile.delete()){
                            log.warning("Cannot delete temporary file: {}", encodedFile.getAbsolutePath());
                        }
                    }
                }
            }, getContext().dispatcher());
        } else {
            unhandled(message);
        }
    }

    protected File encode(File src,
                          CommonAttributes ca,
                          AudioAttributes aa,
                          VideoAttributes va) throws BuilderException, EncodeProcessException {

        //form encode command
        ca.setInputFile(src.getAbsolutePath());
        Command command = new EncodeCommandBuilder(FFMPEG_LOCATION,
                ca,
                va,
                aa).build();
        String resultName = getResultFileName(src.getName(), ca.getFormat());
        command.addAttribute(resultName);

        //start encode process
        try {
            Process p = new ProcessBuilder(command.getCommand())
                    .directory(new File(TMP_DIR)).start();
            if (p.waitFor() != 0) {
                throw new EncodeProcessException(IOUtils.toString(p.getErrorStream()));
            }
        } catch (IOException e) {
            log.error("IOException while encode process: {}", e.getMessage());
            throw new EncodeProcessException(e.getMessage());
        } catch (InterruptedException e) {
            log.error("Encode process interrupted: {}", e.getMessage());
            throw new EncodeProcessException(e.getMessage());
        }

        return Paths.get(TMP_DIR, resultName).toFile();
    }

    private String getResultFileName(String fileName, String format) {
        return ENCODE_RESULT + fileName.substring(0, fileName.indexOf(".")) + "." + format;
    }
}
