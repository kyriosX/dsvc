package com.kyrioslab.dsvc.node.client;

import akka.actor.Actor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.cluster.Cluster;
import com.google.common.base.Strings;
import com.kyrioslab.dsvc.node.messages.LocalMessage;
import com.kyrioslab.dsvc.node.util.FFMPEGService;
import com.kyrioslab.jffmpegw.attributes.AudioAttributes;
import com.kyrioslab.jffmpegw.attributes.CommonAttributes;
import com.kyrioslab.jffmpegw.attributes.VideoAttributes;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Created by Ivan Kirilyuk on 28.12.14.
 *
 */
public class ClientMain {
    public static void main(String[] args) {
        String port = "0";
        String ffmpeg = "ffmpeg";
        String segmentTime = "30";
        String tmpDir = System.getProperty("java.io.tmpdir");
        final String inputFile;
        final String format;
        final String acodec;
        final String vcodec;

        //TODO: deal with this sh
        if (args.length > 0) {
            port = args[0];
        }
        if (args.length > 1) {
            ffmpeg = args[1];
        }
        if (args.length > 2) {
            segmentTime = args[2];
        }
        if (args.length > 3) {
            tmpDir = args[3];
        }
        if (args.length > 4) {
            inputFile = args[4];
        } else {
            inputFile = null;
        }
        if (args.length > 5) {
            format = args[5];
        } else {
            format = null;
        }
        if (args.length > 6) {
            acodec = args[6];
        } else {
            acodec = null;
        }
        if (args.length > 7) {
            vcodec = args[7];
        } else {
            vcodec = null;
        }

        final Config config = ConfigFactory.parseString(
                "akka.remote.netty.tcp.port=" + port + "\n " +
                "akka.cluster.roles = [client]")
                .withFallback(ConfigFactory.load("encode"));

        final ActorSystem system = ActorSystem.create("EncodeSystem", config);

        final FFMPEGService ffmpegService = new FFMPEGService(
                ffmpeg,
                Integer.parseInt(segmentTime),
                tmpDir);

        //#registerOnUp
        Cluster.get(system).registerOnMemberUp(new Runnable() {
            @Override
            public void run() {
                ActorRef client = system.actorOf(Props.create(Client.class, ffmpegService),
                        "encoderClient");

                if (!(Strings.isNullOrEmpty(inputFile)
                        && Strings.isNullOrEmpty(format)
                        && Strings.isNullOrEmpty(acodec))
                        && Strings.isNullOrEmpty(vcodec)) {
                    CommonAttributes ca = new CommonAttributes();
                    AudioAttributes aa = new AudioAttributes();
                    VideoAttributes va = new VideoAttributes();

                    ca.setFormat(format);
                    aa.setCodec(acodec);
                    va.setCodec(vcodec);
                    client.tell(
                            new LocalMessage.EncodeVideoMessage(inputFile,ca,aa,va), null);
                }
            }
        });
        //#registerOnUp
    }

    public static ActorRef startClient(String port,
                                       final FFMPEGService ffmpegService) {

        final Config config = ConfigFactory.parseString(
                "akka.remote.netty.tcp.port=" + port + "\n " +
                        "akka.cluster.roles = [client]")
                .withFallback(ConfigFactory.load("encode"));

        final ActorSystem system = ActorSystem.create("EncodeSystem", config);

        return system.actorOf(Props.create(Client.class, ffmpegService),
                        "encoderClient");
    }
}
