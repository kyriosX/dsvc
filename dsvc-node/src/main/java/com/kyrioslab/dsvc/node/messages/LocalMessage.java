package com.kyrioslab.dsvc.node.messages;

import com.kyrioslab.jffmpegw.attributes.AudioAttributes;
import com.kyrioslab.jffmpegw.attributes.CommonAttributes;
import com.kyrioslab.jffmpegw.attributes.VideoAttributes;

import java.io.Serializable;

/**
 * Created by Ivan Kirilyuk on 29.12.14.
 * <p/>
 * Interface represents messages at local machine.
 */
public interface LocalMessage {

    public static class EncodeVideoMessage implements Serializable {

        private final String pathToVideo;

        private final CommonAttributes commonAttributes;
        private final AudioAttributes audioAttributes;
        private final VideoAttributes videoAttributes;

        /**
         * Message send from GUI to client.
         *
         * @param pathToVideo absolute path to video
         * @param ca          common attributes
         * @param aa          audio attributes
         * @param va          video attributes
         */
        public EncodeVideoMessage(String pathToVideo,
                                  CommonAttributes ca,
                                  AudioAttributes aa,
                                  VideoAttributes va) {

            this.pathToVideo = pathToVideo;
            commonAttributes = ca;
            audioAttributes = aa;
            videoAttributes = va;
        }

        public String getPathToVideo() {
            return pathToVideo;
        }

        public CommonAttributes getCommonAttributes() {
            return commonAttributes;
        }

        public AudioAttributes getAudioAttributes() {
            return audioAttributes;
        }

        public VideoAttributes getVideoAttributes() {
            return videoAttributes;
        }

    }

    public static class EncodeJobFailedMessage implements Serializable {
        private final String reason;
        private final EncodeVideoMessage encodeJob;

        public EncodeJobFailedMessage(String reason, EncodeVideoMessage encodeJob) {
            this.reason = reason;
            this.encodeJob = encodeJob;
        }

        public String getReason() {
            return reason;
        }

        public EncodeVideoMessage getEncodeJob() {
            return encodeJob;
        }

        @Override
        public String toString() {
            return "EncodeJobFailed(" + reason + ")";
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
    public static class TickMessage {}

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
    public class PlaceOnTrackMessage extends TrackPartMessage implements Serializable{

        private final CommonAttributes commonAttributes;
        private final AudioAttributes audioAttributes;
        private final VideoAttributes videoAttributes;

        public PlaceOnTrackMessage(String partId,
                                   CommonAttributes commonAttributes,
                                   AudioAttributes audioAttributes,
                                   VideoAttributes videoAttributes) {
            super(partId);
            this.commonAttributes = commonAttributes;
            this.audioAttributes = audioAttributes;
            this.videoAttributes = videoAttributes;
        }

        public CommonAttributes getCommonAttributes() {
            return commonAttributes;
        }

        public AudioAttributes getAudioAttributes() {
            return audioAttributes;
        }

        public VideoAttributes getVideoAttributes() {
            return videoAttributes;
        }
    }
}
