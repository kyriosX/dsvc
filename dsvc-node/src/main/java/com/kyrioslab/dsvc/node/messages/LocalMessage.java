package com.kyrioslab.dsvc.node.messages;

import akka.cluster.ClusterEvent;
import com.kyrioslab.jffmpegw.command.EncodeCommand;

import java.io.Serializable;

/**
 * Created by Ivan Kirilyuk on 29.12.14.
 * <p/>
 * Interface represents messages at local machine.
 */
public interface LocalMessage {

    public static class EncodeVideoMessage implements Serializable {

        private final String pathToVideo;
        private final EncodeCommand command;
        private final String duration;

        /**
         * Message send from GUI to client.
         *
         * @param pathToVideo absolute path to video
         */
        public EncodeVideoMessage(String pathToVideo,
                                  EncodeCommand command,
                                  String duration) {

            this.pathToVideo = pathToVideo;
            this.command = command;
            this.duration = duration;
        }

        public String getPathToVideo() {
            return pathToVideo;
        }

        public EncodeCommand getCommand() {
            return command;
        }

        public String getDuration() {
            return duration;
        }
    }

    public static class EncodeJobFailedMessage implements Serializable {

        private final String reason;
        private final EncodeCommand command;

        public EncodeJobFailedMessage(String reason,EncodeCommand command) {
            this.reason = reason;
            this.command = command;
        }

        public String getReason() {
            return reason;
        }

        public EncodeCommand getCommand() {
            return command;
        }

        @Override
        public String toString() {
            return "EncodeJobFailed(Command:" + command.getCommand() +
                    " reason: " + reason + ")";
        }
    }

    /**
     * Message, sending from Client to EncodeProcessListener
     * when encoding finished.
     */
    public static class EncodeResult implements Serializable {

        private final String resultPath;

        public EncodeResult(String resultPath) {
            this.resultPath = resultPath;
        }

        public String getResultPath() {
            return resultPath;
        }

    }

    //Part track service messages
    public static abstract class TrackPartMessage implements Serializable {

        private final String partId;

        public TrackPartMessage(String partId) {
            this.partId = partId;
        }

        public String getPartId() {
            return partId;
        }
    }

    /**
     * Message for deleting part from track.
     */
    public static class UntrackPartMessage extends TrackPartMessage implements Serializable {
        public UntrackPartMessage(String partId) {
            super(partId);
        }
    }

    /**
     * Clock tick message
     */
    public static class TickMessage {
    }

    /**
     * Reset part time
     */
    public class ResetPartTimeMessage extends TrackPartMessage implements Serializable {
        public ResetPartTimeMessage(String partId) {
            super(partId);
        }
    }

    /**
     * Place part on track message.
     */
    public class PlaceOnTrackMessage extends TrackPartMessage implements Serializable {

        private final EncodeCommand command;

        public PlaceOnTrackMessage(String partId,
                                   EncodeCommand command) {
            super(partId);
            this.command = command;
        }

        public EncodeCommand getCommand() {
            return command;
        }
    }



    /**
     * Message for requesting client current cluster status.
     */
    public class ClusterStatusRequestMessage implements Serializable {
    }

    /**
     * Current cluster status response
     */
    public class ClusterStatusResponceMessage implements Serializable {

        private final ClusterEvent.CurrentClusterState clusterState;

        private final ClusterEvent.ClusterMetricsChanged metrics;

        public ClusterStatusResponceMessage(ClusterEvent.CurrentClusterState clusterState, ClusterEvent.ClusterMetricsChanged metrics) {
            this.clusterState = clusterState;
            this.metrics = metrics;
        }

        public ClusterEvent.CurrentClusterState getClusterState() {
            return clusterState;
        }

        public ClusterEvent.ClusterMetricsChanged getMetrics() {
            return metrics;
        }
    }

    /**
     * Encoding progress message
     */
    public class ProgressMessage implements Serializable{}

}
