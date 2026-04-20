package com.example.hldsn.mesh;

import com.example.hldsn.mesh.model.MessageStatus;
import com.example.hldsn.mesh.model.MeshIdentity;
import com.example.hldsn.mesh.model.MeshMessage;
import com.example.hldsn.mesh.protocol.MeshEnvelopeCodec;

import org.junit.Assert;
import org.junit.Test;

public class MeshEnvelopeCodecTest {

    @Test
    public void identityCodecRoundTrip() {
        MeshIdentity identity = new MeshIdentity("u-1", "Alice", "Pixel", "pub-key");
        byte[] bytes = MeshEnvelopeCodec.encodeIdentityFrame(identity);
        MeshIdentity decoded = MeshEnvelopeCodec.decodeIdentityFrame(bytes);

        Assert.assertNotNull(decoded);
        Assert.assertEquals("u-1", decoded.getUserId());
        Assert.assertEquals("Alice", decoded.getDisplayName());
    }

    @Test
    public void messageCodecRoundTrip() {
        MeshMessage message = new MeshMessage(
                "m-1",
                "u-1",
                "u-2",
                "cipher",
                123L,
                5,
                1,
                MessageStatus.SENT
        );

        byte[] bytes = MeshEnvelopeCodec.encodeMessageFrame(message);
        MeshMessage decoded = MeshEnvelopeCodec.decodeMessageFrame(bytes);

        Assert.assertNotNull(decoded);
        Assert.assertEquals("m-1", decoded.getMessageId());
        Assert.assertEquals("u-2", decoded.getDestinationId());
        Assert.assertEquals(5, decoded.getTtl());
    }
}

