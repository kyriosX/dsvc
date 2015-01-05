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
}
