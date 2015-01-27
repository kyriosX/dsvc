package com.kyrioslab.dsvc.node.encoder;

import akka.actor.ActorSystem;
import akka.actor.Props;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * Created by Ivan Kirilyuk on 28.12.14.
 *
 */
public class EncoderMain {
    public static void main(String [] args){
        Iterator<String> argsIter = Arrays.asList(args).iterator();

        // Override the configuration of the host and port when specified as program argument
        final String host = argsIter.hasNext() ? argsIter.next() : "localhost";
        final String port = argsIter.hasNext() ? argsIter.next() : "0";
        StringBuilder seedNodes = new StringBuilder("[");
        while (argsIter.hasNext()) {
            seedNodes.append("\"").append(argsIter.next()).append("\"");
            if (argsIter.hasNext()) {
                seedNodes.append(",");
            }
        }
        seedNodes.append("]");

        final Config config = ConfigFactory.parseString(
                "akka.remote.netty.tcp.port=" + port
                + "\n akka.cluster.roles = [encoder]"
                + "\n akka.remote.netty.tcp.hostname=" + host
                + "\n akka.cluster.seed-nodes=" + seedNodes.toString())
               .withFallback(ConfigFactory.load("encode_system"));

        ActorSystem system = ActorSystem.create("EncodeSystem", config);
        system.actorOf(Props.create(Encoder.class), "videoEncoder");

        system.actorOf(Props.create(Encoder.class), "metricsListener");
    }
}
