package com.kyrioslab.dsvc.node.messages;

import com.kyrioslab.jffmpegw.attributes.Attributes;
import com.kyrioslab.jffmpegw.attributes.AudioAttributes;
import com.kyrioslab.jffmpegw.attributes.CommonAttributes;
import com.kyrioslab.jffmpegw.attributes.VideoAttributes;
import com.kyrioslab.jffmpegw.command.EncodeCommand;

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

        private final EncodeCommand command;

        public EncodeVideoPartMessage(String partId, byte[] payload,
                                      EncodeCommand command) {
            this.partId = partId;
            this.payload = payload.clone();
            this.command = command;
        }

        public String getPartId() {
            return partId;
        }

        public byte[] getPayload() {
            return payload;
        }

        public EncodeCommand getCommand() {
            return command;
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
        private final EncodeCommand command;

        public EncodePartFailed(String reason, String partId,
                                EncodeCommand command) {
            this.reason = reason;
            this.partId = partId;
            this.command = command;
        }

        public String getReason() {
            return reason;
        }

        public String getPartId() {
            return partId;
        }

        public EncodeCommand getCommand() {
            return command;
        }

        @Override
        public String toString() {
            return "EncodePartJobFailed(" + reason + ")" + ", part id:" + partId;
        }
    }

}
