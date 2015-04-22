package com.kyrioslab.dsvc.node.client;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.cluster.Cluster;
import com.google.common.base.Strings;
import com.kyrioslab.dsvc.node.messages.LocalMessage;
import com.kyrioslab.dsvc.node.util.FFMPEGService;
import com.kyrioslab.jffmpegw.attributes.parser.StreamInfo;
import com.kyrioslab.jffmpegw.command.BuilderException;
import com.kyrioslab.jffmpegw.command.EncodeCommand;
import com.kyrioslab.jffmpegw.command.EncodeCommandBuilder;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.util.Arrays;

/**
 * Created by Ivan Kirilyuk on 28.12.14.
 */
public class ClientMain {
    public static void main(String[] args) {
        String port = "0";
        final String ffmpeg;
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
        } else {
            ffmpeg = "/home/wizzard/diploma_work/dsvc/ffmpeg/ffmpeg";
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
            inputFile = "/home/wizzard/diploma_work/dsvc/ffmpeg/test.mp4";
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
                .withFallback(ConfigFactory.load("encode_system"));

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

                    StreamInfo vs = new StreamInfo();
                    vs.setIndex(0);
                    vs.setCodecName("mpeg4");
                    vs.setCodecType("video");
                    vs.setWidth("420");
                    vs.setHeight("360");
                    vs.setAvgFrameRate("15");
                    vs.setBitRate("196000");

                    StreamInfo as = new StreamInfo();
                    as.setIndex(1);
                    as.setCodecName("copy");
                    as.setCodecType("audio");

                    EncodeCommand command = null;
                    try {
                        command = new EncodeCommandBuilder(ffmpeg,
                                Arrays.asList(vs, as)).build();
                    } catch (BuilderException e) {
                        e.printStackTrace();
                    }
                    command.setFormats("mp4", "mp4");

                    client.tell(
                            new LocalMessage.EncodeVideoMessage(inputFile, command, "10"), null);
                }
            }
        });
        //#registerOnUp
    }

    public static ActorRef startClient(
            final FFMPEGService ffmpegService, String host, String port, String seedNodes) {

        final Config config = ConfigFactory.parseString(
                "akka.remote.netty.tcp.port=" + port
                        + "\n akka.cluster.roles = [client]"
                        + "\n akka.remote.netty.tcp.hostname=" + host
                        + "\n akka.cluster.seed-nodes=" + seedNodes)
                .withFallback(ConfigFactory.load("encode_system"));

        final ActorSystem system = ActorSystem.create("EncodeSystem", config);

        return system.actorOf(Props.create(Client.class, ffmpegService),
                "encoderClient");
    }
}
