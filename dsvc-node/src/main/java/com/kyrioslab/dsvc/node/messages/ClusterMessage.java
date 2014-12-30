package com.kyrioslab.dsvc.node.messages;

import com.kyrioslab.jffmpegw.attributes.Attributes;
import com.kyrioslab.jffmpegw.attributes.AudioAttributes;
import com.kyrioslab.jffmpegw.attributes.CommonAttributes;
import com.kyrioslab.jffmpegw.attributes.VideoAttributes;

import java.io.Serializable;

/**
 * Created by Ivan Kirilyuk on 29.12.14.
 *
 * Interface represents cluster messages.
 */
public interface ClusterMessage {

    public static class EncodeVideoPartMessage implements Serializable{

        private final String partId;
        private final byte[] payload;

        private final CommonAttributes commonAttributes;
        private final AudioAttributes audioAttributes;
        private final VideoAttributes videoAttributes;

        public EncodeVideoPartMessage(String partId, byte[] payload,
                                      CommonAttributes ca,
                                      AudioAttributes aa,
                                      VideoAttributes va) {
            this.partId = partId;
            this.payload = payload.clone();
            commonAttributes = ca;
            audioAttributes = aa;
            videoAttributes = va;
        }

        public String getPartId() {
            return partId;
        }

        public byte[] getPayload() {
            return payload;
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

    public static class EncodeResultPartMessage implements Serializable{

        private final String format;
        private final String partId;
        private final byte[] payload;

        public EncodeResultPartMessage(String partId, byte[] payload, String format) {
            this.format = format;
            this.partId = partId;
            this.payload = payload;
        }

        public String getFormat() {
            return format;
        }

        public String getPartId() {
            return partId;
        }

        public byte[] getPayload() {
            return payload;
        }
    }

    public static class EncodePartFailed implements Serializable {
        private final String reason;
        private final String partId;

        public EncodePartFailed(String reason, String partId) {
            this.reason = reason;
            this.partId = partId;
        }

        public String getReason() {
            return reason;
        }

        public String getPartId() {
            return partId;
        }

        @Override
        public String toString() {
            return "EncodePartJobFailed(" + reason + ")" + ", part id:" + partId;
        }
    }

}
