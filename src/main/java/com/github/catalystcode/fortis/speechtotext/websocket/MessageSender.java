package com.github.catalystcode.fortis.speechtotext.websocket;

import com.github.catalystcode.fortis.speechtotext.telemetry.AudioTelemetry;
import com.github.catalystcode.fortis.speechtotext.telemetry.CallsTelemetry;
import com.github.catalystcode.fortis.speechtotext.telemetry.ConnectionTelemetry;
import com.github.catalystcode.fortis.speechtotext.messages.BinaryMessageCreator;
import org.apache.log4j.Logger;

import java.io.InputStream;
import java.nio.ByteBuffer;

import static com.github.catalystcode.fortis.speechtotext.constants.SpeechServiceContentTypes.JSON;
import static com.github.catalystcode.fortis.speechtotext.constants.SpeechServiceContentTypes.WAV;
import static com.github.catalystcode.fortis.speechtotext.constants.SpeechServiceLimitations.MAX_BYTES_PER_AUDIO_CHUNK;
import static com.github.catalystcode.fortis.speechtotext.constants.SpeechServicePaths.*;
import static com.github.catalystcode.fortis.speechtotext.messages.TextMessageCreator.createTextMessage;
import static com.github.catalystcode.fortis.speechtotext.utils.ProtocolUtils.newGuid;

public abstract class MessageSender {
    private static final Logger log = Logger.getLogger(MessageSender.class);

    private final String connectionId;
    private final String requestId;

    protected MessageSender(String connectionId) {
        this.connectionId = connectionId;
        this.requestId = newGuid();
    }

    public final void sendConfiguration() {
        String config = new PlatformInfo().toJson();
        String configMessage = createTextMessage(SPEECH_CONFIG, requestId, JSON, config);
        sendTextMessage(configMessage);
        log.info("Sent speech config: " + config);
    }

    public final void sendAudio(InputStream wavStream) {
        AudioTelemetry audioTelemetry = AudioTelemetry.forId(requestId);
        audioTelemetry.recordAudioStarted();
        BinaryMessageCreator binaryMessageCreator = new BinaryMessageCreator();
        try {
            byte[] buf = new byte[MAX_BYTES_PER_AUDIO_CHUNK];
            int chunksSent = 0;
            int read;
            while ((read = wavStream.read(buf)) != -1) {
                ByteBuffer audioChunkMessage = binaryMessageCreator.createBinaryMessage(AUDIO, requestId, WAV, buf, read);
                sendBinaryMessage(audioChunkMessage);
                chunksSent++;
                log.debug("Sent audio chunk " + chunksSent + "with " + read + " bytes");
            }
            ByteBuffer audioEndMessage = binaryMessageCreator.createBinaryMessage(AUDIO, requestId, WAV, new byte[0], 0);
            sendBinaryMessage(audioEndMessage);
            log.info("Sent " + chunksSent + " audio chunks");
        } catch (Exception ex) {
            audioTelemetry.recordAudioFailed(ex.getMessage());
            throw new RuntimeException(ex);
        } finally {
            audioTelemetry.recordAudioEnded();
        }
    }

    public final void sendTelemetry() {
        CallsTelemetry callsTelemetry = CallsTelemetry.forId(requestId);
        ConnectionTelemetry connectionTelemetry = ConnectionTelemetry.forId(connectionId);
        AudioTelemetry audioTelemetry = AudioTelemetry.forId(requestId);
        String telemetry = new TelemetryInfo(connectionId, callsTelemetry, connectionTelemetry, audioTelemetry).toJson();
        String telemetryMessage = createTextMessage(TELEMETRY, requestId, JSON, telemetry);
        sendTextMessage(telemetryMessage);
        log.info("Sent telemetry: " + telemetry);
    }

    protected abstract void sendBinaryMessage(ByteBuffer message);
    protected abstract void sendTextMessage(String message);
}
