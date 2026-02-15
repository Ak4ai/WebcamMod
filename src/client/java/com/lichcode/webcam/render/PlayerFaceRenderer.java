package com.lichcode.webcam.render;

import com.lichcode.webcam.PlayerFeeds;
import com.lichcode.webcam.render.image.RenderableImage;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public class PlayerFaceRenderer extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel>  {
    
    private static final Map<String, Identifier> registeredTextures = new HashMap<>();
    private static final Map<String, NativeImageBackedTexture> textureCache = new HashMap<>();
    private static final Map<String, int[]> textureSizes = new HashMap<>();

    public PlayerFaceRenderer(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
        super(context);
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, PlayerEntityRenderState state, float limbAngle, float limbDistance) {
        ClientPlayNetworkHandler clientPlayNetworkHandler = MinecraftClient.getInstance().getNetworkHandler();
        if (clientPlayNetworkHandler == null) {
            return;
        }
        PlayerListEntry playerListEntry = clientPlayNetworkHandler.getPlayerListEntry(state.name);
        if (playerListEntry == null) {
            return;
        }

        String playerUUID = playerListEntry.getProfile().getId().toString();
        // Get the renderable image that represents the current video frame
        // if it is null, then we haven't received any video from them so we don't attempt to render
        RenderableImage image = PlayerFeeds.get(playerUUID);
        if (image == null) {
            return;
        }

        // Get or create registered texture for this player
        Identifier textureId = getOrCreateTexture(playerUUID, image);
        if (textureId == null) {
            return;
        }

        matrices.push();

        ModelPart head = getContextModel().head;
        // Apply head rotation to the matrix stack
        head.applyTransform(matrices);

        matrices.translate(0, 0, -0.30);
        matrices.scale(0.25f, 0.5f, 1f);

        MatrixStack.Entry entry = matrices.peek();
        Matrix4f positionMatrix = entry.getPositionMatrix();

        // Use the Minecraft texture system with registered texture
        RenderLayer renderLayer = RenderLayer.getEntityTranslucent(textureId);
        VertexConsumer vertexConsumer = vertexConsumers.getBuffer(renderLayer);

        // Draw quad with the webcam texture
        int overlay = OverlayTexture.DEFAULT_UV;
        
        vertexConsumer.vertex(positionMatrix, 1, -1, 0).color(255, 255, 255, 255).texture(0, 0).overlay(overlay).light(light).normal(entry, 0, 0, -1);
        vertexConsumer.vertex(positionMatrix, 1, 0, 0).color(255, 255, 255, 255).texture(0, 1).overlay(overlay).light(light).normal(entry, 0, 0, -1);
        vertexConsumer.vertex(positionMatrix, -1, 0, 0).color(255, 255, 255, 255).texture(1, 1).overlay(overlay).light(light).normal(entry, 0, 0, -1);
        vertexConsumer.vertex(positionMatrix, -1, -1, 0).color(255, 255, 255, 255).texture(1, 0).overlay(overlay).light(light).normal(entry, 0, 0, -1);

        matrices.pop();
    }
    
    private Identifier getOrCreateTexture(String playerUUID, RenderableImage image) {
        // Check if we need to recreate the texture due to size change
        int[] existingSize = textureSizes.get(playerUUID);
        if (existingSize != null && (existingSize[0] != image.width || existingSize[1] != image.height)) {
            // Size changed, destroy and recreate
            Identifier oldTextureId = registeredTextures.remove(playerUUID);
            if (oldTextureId != null) {
                MinecraftClient.getInstance().getTextureManager().destroyTexture(oldTextureId);
            }
            textureCache.remove(playerUUID);
            textureSizes.remove(playerUUID);
        }
        
        NativeImageBackedTexture texture = textureCache.get(playerUUID);
        
        if (texture == null && image.width > 0 && image.height > 0) {
            // Create new texture with the new constructor (name, width, height, useMipmaps)
            String textureName = "webcam_" + playerUUID.replace("-", "");
            texture = new NativeImageBackedTexture(textureName, image.width, image.height, false);
            
            Identifier textureId = Identifier.of("webcam", "player_webcam_" + playerUUID.replace("-", ""));
            MinecraftClient.getInstance().getTextureManager().registerTexture(textureId, texture);
            
            textureCache.put(playerUUID, texture);
            registeredTextures.put(playerUUID, textureId);
            textureSizes.put(playerUUID, new int[]{image.width, image.height});
        }
        
        if (texture != null) {
            // Update the texture with new image data
            updateTexture(texture, image);
        }
        
        return registeredTextures.get(playerUUID);
    }
    
    private void updateTexture(NativeImageBackedTexture texture, RenderableImage image) {
        NativeImage nativeImage = texture.getImage();
        if (nativeImage == null) {
            return;
        }
        
        ByteBuffer data = image.data();
        if (data == null) {
            return;
        }
        
        data.rewind();
        
        // Copy RGB data to RGBA NativeImage
        for (int y = 0; y < Math.min(image.height, nativeImage.getHeight()); y++) {
            for (int x = 0; x < Math.min(image.width, nativeImage.getWidth()); x++) {
                int r = data.get() & 0xFF;
                int g = data.get() & 0xFF;
                int b = data.get() & 0xFF;
                // NativeImage uses ABGR format
                int color = (255 << 24) | (b << 16) | (g << 8) | r;
                nativeImage.setColorArgb(x, y, color);
            }
        }
        
        texture.upload();
    }
    
    public static void cleanup() {
        for (Identifier textureId : registeredTextures.values()) {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(textureId);
        }
        textureCache.clear();
        registeredTextures.clear();
        textureSizes.clear();
    }
}
