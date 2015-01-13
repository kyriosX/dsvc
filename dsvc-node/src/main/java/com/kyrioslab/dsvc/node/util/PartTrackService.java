package com.kyrioslab.dsvc.node.util;

import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import com.kyrioslab.dsvc.node.messages.ClusterMessage;
import com.kyrioslab.dsvc.node.messages.LocalMessage;
import org.apache.commons.collections.buffer.CircularFifoBuffer;
import scala.concurrent.duration.FiniteDuration;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

/**
 * Created by Ivan Kirilyuk on 08.01.15.
 * Service for tracking video parts.
 */
public class PartTrackService extends UntypedActor {

    private LoggingAdapter log = Logging.getLogger(getContext().system(), this);

    public static final Double VAR_MULTIPLIER = 3.0;
    public static final int PART_BUFFER_SIZE = 100;
    public static final Double DEFAULT_AVG_TIME = 300.0; // 5 min
    /**
     * List for traking how long parts are on encoding.
     */
    private Queue<PartTime> partList = new ConcurrentLinkedQueue<>();

    /**
     * Success part time for avg time calculation.
     */
    private CircularFifoBuffer successPartTime = new CircularFifoBuffer(PART_BUFFER_SIZE);

    /**
     * Parts average encoding time.
     */
    private double avgTime = DEFAULT_AVG_TIME;

    /**
     * Client actor reference.
     */
    private final ActorRef client;

    /**
     * Generates tick messages
     */
    private Cancellable clock;

    public PartTrackService(ActorRef client) {
        this.client = client;
    }

    @Override
    public void preStart() {
        startClock();
    }

    @Override
    public void onReceive(Object message) throws Exception {
        if (message instanceof LocalMessage.TickMessage) {
            for (PartTime pt : partList) {
                pt.inc();

                //if part is timed out, send failed message
                if (pt.getTime() >= avgTime * VAR_MULTIPLIER) {
                    client.tell(new ClusterMessage.EncodePartFailed(
                                    "Part timed out: " + pt.getTime(),
                                    pt.getPartId(),
                                    pt.getSavedMsg().getCommand()
                            ),
                            getSelf());
                }
            }
            if (partList.size() == 0) {
                stopClock();
            }
        } else if (message instanceof LocalMessage.PlaceOnTrackMessage) {
            LocalMessage.PlaceOnTrackMessage msg =
                    (LocalMessage.PlaceOnTrackMessage)message;
            log.info("Registered part {}", msg.getPartId());
            partList.add(new PartTime(msg.getPartId(), msg));

            //starts clock if not started yet
            startClock();
        } else if (message instanceof LocalMessage.ResetPartTimeMessage) {
            final String partId = ((LocalMessage.ResetPartTimeMessage) message).getPartId();
            if (resetPartTime(partId)) {
                log.info("Time reset done on part {}", partId);
            } else {
                log.warning("Time reset queried for non registered part {}", partId);
            }
        } else if (message instanceof LocalMessage.UntrackPartMessage) {
            LocalMessage.TrackPartMessage msg =
                    ((LocalMessage.TrackPartMessage) message);

            final String partId = msg.getPartId();
            untrackPart(partId);
        } else {
            unhandled(message);
        }
    }

    private boolean resetPartTime(String partId) {
        for (PartTime pt : partList) {
            if (pt.getPartId().equals(partId)) {
                pt.reset();
                return true;
            }
        }
        return false;
    }

    private void recalculateAvg() {
        long comTime = 0;
        for (Object time : successPartTime) {
            comTime += (Long) time;
        }
        if (comTime != 0 && successPartTime.size() != 0) {
            avgTime = comTime / (double) successPartTime.size();
            log.info("Average part encoding time {}s", avgTime);
        }
    }

    private void untrackPart(String partId) {
        if (removePart(partId)) {
            log.info("Part {} removed from track", partId);
            recalculateAvg();
        } else {
            log.info("Cannot fond part {}", partId);
        }
    }

    private boolean removePart(String partId) {
        for (PartTime pt : partList) {
            if (pt.getPartId().equals(partId)) {
                successPartTime.add(pt.getTime());
                return partList.remove(pt);
            }
        }
        return false;
    }

    private void startClock() {
        if (clock == null || clock.isCancelled()) {
            clock = getContext().system().scheduler().schedule(
                    FiniteDuration.apply(1, TimeUnit.SECONDS),
                    FiniteDuration.apply(1, TimeUnit.SECONDS),
                    new Runnable() {
                        @Override
                        public void run() {
                            getSelf().tell(new LocalMessage.TickMessage(), null);
                        }
                    },
                    getContext().dispatcher());
        }
    }

    private void stopClock() {
        if (!clock.isCancelled()) {
            clock.cancel();
            avgTime = DEFAULT_AVG_TIME;
        }
    }
}
