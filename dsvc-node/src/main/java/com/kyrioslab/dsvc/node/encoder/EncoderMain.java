package com.kyrioslab.dsvc.node.encoder;

import akka.actor.ActorSystem;
import akka.actor.Props;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Created by Ivan Kirilyuk on 28.12.14.
 *
 */
public class EncoderMain {
    public static void main(String [] args){
        // Override the configuration of the port when specified as program argument
        final String port = args.length > 0 ? args[0] : "0";
        final Config config = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + port + "\n akka.cluster.roles = [encoder]")
               .withFallback(ConfigFactory.load("encode"));

        ActorSystem system = ActorSystem.create("EncodeSystem", config);
        system.actorOf(Props.create(Encoder.class), "videoEncoder");

        system.actorOf(Props.create(Encoder.class), "metricsListener");
    }
}
