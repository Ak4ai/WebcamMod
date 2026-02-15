package com.lichcode.webcam;

/**
 * Stores webcam rendering settings
 */
public class WebcamSettings {
    // Gamma value for brightness adjustment (1.0 = normal, <1.0 = darker, >1.0 = brighter)
    private static float gamma = 1.0f;
    
    // Min and max gamma values
    public static final float MIN_GAMMA = 0.2f;
    public static final float MAX_GAMMA = 3.0f;
    
    // Show webcam on player face (3D model)
    private static boolean faceWebcamEnabled = true;
    
    // Show floating webcams in top-right corner
    private static boolean floatingWebcamsEnabled = false;
    
    // Size of floating webcams (in pixels)
    private static int floatingWebcamSize = 80;
    public static final int MIN_FLOATING_SIZE = 40;
    public static final int MAX_FLOATING_SIZE = 200;
    
    public static float getGamma() {
        return gamma;
    }
    
    public static void setGamma(float value) {
        gamma = Math.max(MIN_GAMMA, Math.min(MAX_GAMMA, value));
    }
    
    public static boolean isFaceWebcamEnabled() {
        return faceWebcamEnabled;
    }
    
    public static void setFaceWebcamEnabled(boolean enabled) {
        faceWebcamEnabled = enabled;
    }
    
    public static void toggleFaceWebcam() {
        faceWebcamEnabled = !faceWebcamEnabled;
    }
    
    public static boolean isFloatingWebcamsEnabled() {
        return floatingWebcamsEnabled;
    }
    
    public static void setFloatingWebcamsEnabled(boolean enabled) {
        floatingWebcamsEnabled = enabled;
    }
    
    public static void toggleFloatingWebcams() {
        floatingWebcamsEnabled = !floatingWebcamsEnabled;
    }
    
    public static int getFloatingWebcamSize() {
        return floatingWebcamSize;
    }
    
    public static void setFloatingWebcamSize(int size) {
        floatingWebcamSize = Math.max(MIN_FLOATING_SIZE, Math.min(MAX_FLOATING_SIZE, size));
    }
    
    /**
     * Apply gamma correction to a color value (0-255)
     */
    public static int applyGamma(int colorValue) {
        // Normalize to 0-1 range
        float normalized = colorValue / 255.0f;
        // Apply gamma correction
        float corrected = (float) Math.pow(normalized, 1.0f / gamma);
        // Convert back to 0-255 range
        return Math.min(255, Math.max(0, Math.round(corrected * 255.0f)));
    }
}
