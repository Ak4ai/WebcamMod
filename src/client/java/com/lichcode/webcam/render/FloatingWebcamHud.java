package com.lichcode.webcam.render;

import com.lichcode.webcam.PlayerFeeds;
import com.lichcode.webcam.WebcamSettings;
import com.lichcode.webcam.render.image.RenderableImage;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.*;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.nio.ByteBuffer;
import java.util.*;

/**
 * Renders floating webcams in the top-right corner of the screen
 */
public class FloatingWebcamHud implements HudRenderCallback {
    
    private static final int PADDING = 5;
    private static final int SPACING = 5;
    private static final int BORDER_SIZE = 2;
    
    // Cache for textures (same approach as PlayerFaceRenderer)
    private static final Map<String, Identifier> registeredTextures = new HashMap<>();
    private static final Map<String, NativeImageBackedTexture> textureCache = new HashMap<>();
    private static final Map<String, int[]> textureSizes = new HashMap<>();
    
    @Override
    public void onHudRender(DrawContext drawContext, RenderTickCounter tickCounter) {
        if (!WebcamSettings.isFloatingWebcamsEnabled()) {
            return;
        }
        
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.getNetworkHandler() == null) {
            return;
        }
        
        // Get all online players
        Collection<PlayerListEntry> players = client.getNetworkHandler().getPlayerList();
        if (players.isEmpty()) {
            return;
        }
        
        int screenWidth = client.getWindow().getScaledWidth();
        int webcamSize = WebcamSettings.getFloatingWebcamSize();
        
        int x = screenWidth - PADDING - webcamSize;
        int y = PADDING;
        
        // Render webcams for each player that has a feed
        for (PlayerListEntry player : players) {
            String uuid = player.getProfile().getId().toString();
            RenderableImage image = PlayerFeeds.get(uuid);
            
            if (image != null && image.data != null && image.width > 0 && image.height > 0) {
                // Get player name first
                String playerName = player.getProfile().getName();
                if (playerName == null || playerName.isEmpty()) {
                    playerName = "Player";
                }
                
                // Render this player's webcam
                x = renderSingleWebcam(drawContext, client, uuid, playerName, image, x, y, webcamSize);
                
                // If we run out of space, stop
                if (x < PADDING) {
                    break;
                }
            }
        }
        

    }
    
    private int renderSingleWebcam(DrawContext drawContext, MinecraftClient client, String uuid, String playerName, RenderableImage image, int x, int y, int webcamSize) {
        // Draw border/background
        drawContext.fill(
            x - BORDER_SIZE, 
            y - BORDER_SIZE, 
            x + webcamSize + BORDER_SIZE, 
            y + webcamSize + BORDER_SIZE, 
            0xFF000000  // Black border
        );
        
        // Draw the webcam texture
        renderWebcamTexture(drawContext, uuid, image, x, y, webcamSize);
        
        // Draw player name below
        int nameWidth = client.textRenderer.getWidth(playerName);
        int nameX = x + (webcamSize - nameWidth) / 2;
        int nameY = y + webcamSize + BORDER_SIZE + 2;
        
        // Background for name (wider to fit text better)
        drawContext.fill(nameX - 3, nameY - 2, nameX + nameWidth + 3, nameY + 11, 0xCC000000);
        drawContext.drawText(client.textRenderer, playerName, nameX, nameY, 0xFFFFFFFF, true);
        
        // Return next X position
        return x - webcamSize - SPACING - BORDER_SIZE * 2;
    }
    
    private void renderWebcamTexture(DrawContext drawContext, String uuid, RenderableImage image, int x, int y, int size) {
        if (image == null || image.data == null || image.width <= 0 || image.height <= 0) {
            return;
        }
        
        // Get or create the registered texture and update it with latest frame data
        Identifier textureId = getOrCreateTexture(uuid, image);
        if (textureId == null) {
            return;
        }
        
        // Use scissor to clip rendering strictly within the box
        drawContext.enableScissor(x, y, x + size, y + size);
        
        // 10-param drawTexture: (pipeline, id, x, y, u, v, width, height, texW, texH)
        // By setting width=height=texW=texH=size, the UV maps 0.0 to 1.0
        // covering the full texture, and it displays at exactly size x size pixels
        drawContext.drawTexture(
            RenderPipelines.GUI_TEXTURED,
            textureId,
            x, y,                // screen position
            0.0f, 0.0f,          // UV offset (float)
            size, size,          // display width/height
            size, size           // "texture" dimensions = same as display = UV 0..1
        );
        
        drawContext.disableScissor();
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
            // Create new texture (same as PlayerFaceRenderer)
            String textureName = "webcam_hud_" + playerUUID.replace("-", "");
            texture = new NativeImageBackedTexture(textureName, image.width, image.height, false);
            
            Identifier textureId = Identifier.of("webcam", "hud_webcam_" + playerUUID.replace("-", ""));
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
        
        // Use absolute get(index) instead of relative get() to avoid conflicts
        // with PlayerFaceRenderer reading the same buffer
        int imgW = Math.min(image.width, nativeImage.getWidth());
        int imgH = Math.min(image.height, nativeImage.getHeight());
        
        for (int y = 0; y < imgH; y++) {
            for (int x = 0; x < imgW; x++) {
                int pos = (y * image.width + x) * 3;
                if (pos + 2 >= data.capacity()) {
                    continue;
                }
                int r = WebcamSettings.applyGamma(data.get(pos) & 0xFF);
                int g = WebcamSettings.applyGamma(data.get(pos + 1) & 0xFF);
                int b = WebcamSettings.applyGamma(data.get(pos + 2) & 0xFF);
                // NativeImage uses ABGR format
                int color = (255 << 24) | (b << 16) | (g << 8) | r;
                nativeImage.setColorArgb(x, y, color);
            }
        }
        
        texture.upload();
    }
    
    /**
     * Clean up all cached textures
     */
    public static void cleanup() {
        MinecraftClient client = MinecraftClient.getInstance();
        for (Identifier id : registeredTextures.values()) {
            client.getTextureManager().destroyTexture(id);
        }
        for (NativeImageBackedTexture texture : textureCache.values()) {
            texture.close();
        }
        registeredTextures.clear();
        textureCache.clear();
        textureSizes.clear();
    }
}
