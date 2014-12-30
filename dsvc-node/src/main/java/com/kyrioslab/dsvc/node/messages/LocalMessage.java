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

    public static class SplitVideoMessage implements Serializable {

        private final String videoAbsolutePath;

        public SplitVideoMessage(String videoAbsolutePath) {
            this.videoAbsolutePath = videoAbsolutePath;
        }

        public String getVideoAbsolutePath() {
            return videoAbsolutePath;
        }
    }

    public static class SplitFinishedMessage implements Serializable {

        private final String partsDir;

        public SplitFinishedMessage(String partsDir) {
            this.partsDir = partsDir;
        }

        public String getPartsDir() {
            return partsDir;
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

    public static final String ENCODER_REGISTRATION = "EncoderRegistration";

}
