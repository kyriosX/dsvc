package com.kyrioslab.dsvc.node;

/**
 * Created by Ivan Kirilyuk on 28.12.14.
 */

import akka.actor.UntypedActor;
import akka.cluster.Cluster;
import akka.cluster.ClusterEvent;
import akka.cluster.ClusterEvent.MemberEvent;
import akka.cluster.ClusterEvent.MemberUp;
import akka.cluster.ClusterEvent.MemberRemoved;
import akka.cluster.ClusterEvent.UnreachableMember;
import akka.dispatch.Mapper;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import com.kyrioslab.dsvc.node.messages.ClusterMessage;
import com.kyrioslab.jffmpegw.attributes.AudioAttributes;
import com.kyrioslab.jffmpegw.attributes.CommonAttributes;
import com.kyrioslab.jffmpegw.attributes.VideoAttributes;
import com.kyrioslab.jffmpegw.command.BuilderException;
import com.kyrioslab.jffmpegw.command.Command;
import com.kyrioslab.jffmpegw.command.EncodeCommand;
import com.kyrioslab.jffmpegw.command.EncodeCommandBuilder;
import org.apache.commons.io.FileUtils;
import scala.concurrent.Future;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Paths;
import java.util.concurrent.Callable;

import static akka.dispatch.Futures.future;
import static akka.pattern.Patterns.pipe;


public class Encoder extends UntypedActor {

    public static final String ENCODE_RESULT = "r-";

    private LoggingAdapter log = Logging.getLogger(getContext().system(), this);

    @Override
    public void onReceive(Object message) {
        if (message instanceof ClusterMessage.EncodeVideoPartMessage) {
            final ClusterMessage.EncodeVideoPartMessage msg =
                    (ClusterMessage.EncodeVideoPartMessage) message;

            try {
                final File src = Paths.get(Client.TMP_DIR, msg.getPartId()).toFile();
                FileUtils.writeByteArrayToFile(src, msg.getPayload());
                Future<File> f = future(new Callable<File>() {
                    public File call() {
                        try {
                            return encode(src, msg.getCommonAttributes(), msg.getAudioAttributes(), msg.getVideoAttributes());
                        } catch (BuilderException e) {
                            e.printStackTrace();
                            return null;
                        }
                    }
                }, getContext().dispatcher());

                Future<ClusterMessage.EncodeResultPartMessage> result = f.map(
                        new Mapper<File, ClusterMessage.EncodeResultPartMessage>() {
                            public ClusterMessage.EncodeResultPartMessage apply(File encodedPart) {
                                try {
                                    return new ClusterMessage.EncodeResultPartMessage(msg.getPartId(),
                                            FileUtils.readFileToByteArray(encodedPart),
                                            encodedPart.getName().substring(encodedPart.getName().indexOf(".") + 1));
                                } catch (IOException e) {
                                    e.printStackTrace();
                                    return new ClusterMessage.EncodeResultPartMessage(null, null, null);
                                }
                            }
                        }, getContext().dispatcher());

                pipe(result, getContext().dispatcher()).to(getSender());
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            unhandled(message);
        }
    }

    protected File encode(File src, CommonAttributes ca, AudioAttributes aa, VideoAttributes va) throws BuilderException {
        ca.setInputFile(src.getAbsolutePath());
        Command command = new EncodeCommandBuilder(Client.FFMPEGLocation,
                ca,
                va,
                aa).build();
        String resultFileName = ENCODE_RESULT + src.getName().substring(0,src.getName().indexOf(".")) + "." + ca.getFormat();
        command.addAttribute(resultFileName);
        try {
            Process p = new ProcessBuilder(command.getCommand())
                    .directory(new File(Client.TMP_DIR)).start();
            int code = p.waitFor();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return Paths.get(Client.TMP_DIR, resultFileName).toFile();
    }
}
