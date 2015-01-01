package com.kyrioslab.dsvc.node.client;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.cluster.Cluster;
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

        final String port = args.length > 0 ? args[0] : "2551";
        final Config config = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + port + "\n " +
                "akka.cluster.roles = [client]")
                .withFallback(ConfigFactory.load("encode"));

        final ActorSystem system = ActorSystem.create("EncodeSystem", config);

        final FFMPEGService ffmpegService = new FFMPEGService(ClientMain.class.getResource("/ffmpeg").getPath(),
                30,
                System.getProperty("java.io.tmpdir"));

        //#registerOnUp
        Cluster.get(system).registerOnMemberUp(new Runnable() {
            @Override
            public void run() {
                ActorRef client = system.actorOf(Props.create(Client.class, ffmpegService),
                        "encoderClient");
                CommonAttributes ca = new CommonAttributes();
                ca.setFormat("mp4");
                AudioAttributes aa = new AudioAttributes();
                aa.setCodec("copy");
                VideoAttributes va = new VideoAttributes();
                va.setCodec("mpeg4");
                client.tell(new LocalMessage.EncodeVideoMessage(
                        ClientMain.class.getResource("/u4.mp4").getPath(),
                        ca,
                        aa,
                        va
                ), null);
            }
        });
        //#registerOnUp
    }
}
