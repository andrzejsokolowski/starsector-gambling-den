package gamblingden.ui;

import java.awt.Color;

import org.lwjgl.opengl.GL11;

/**
 * Small drawing helpers for the cabinet.
 *
 * Everything here draws in the panel's own coordinates, where y grows upwards.
 * Callers are expected to have already set up blending, which the panel does once per frame.
 */
public final class GLDraw {

    private GLDraw() {
    }

    public static Color brighten(Color c, float amount) {
        return new Color(
                Math.min(255, (int) (c.getRed() + (255 - c.getRed()) * amount)),
                Math.min(255, (int) (c.getGreen() + (255 - c.getGreen()) * amount)),
                Math.min(255, (int) (c.getBlue() + (255 - c.getBlue()) * amount)),
                c.getAlpha());
    }

    public static Color darken(Color c, float amount) {
        float keep = 1f - amount;
        return new Color(
                (int) (c.getRed() * keep),
                (int) (c.getGreen() * keep),
                (int) (c.getBlue() * keep),
                c.getAlpha());
    }

    public static Color mix(Color a, Color b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        return new Color(
                (int) (a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t),
                (int) (a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t));
    }

    private static void setColor(Color c, float alphaMult) {
        GL11.glColor4f(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f,
                (c.getAlpha() / 255f) * alphaMult);
    }

    public static void quad(float x, float y, float w, float h, Color color, float alphaMult) {
        setColor(color, alphaMult);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x, y);
        GL11.glVertex2f(x + w, y);
        GL11.glVertex2f(x + w, y + h);
        GL11.glVertex2f(x, y + h);
        GL11.glEnd();
    }

    /** A rectangle lit from the top left, so it reads as a raised panel. */
    public static void bevelledPanel(float x, float y, float w, float h, Color base,
                                     float thickness, float alphaMult) {
        Color light = brighten(base, 0.35f);
        Color dark = darken(base, 0.45f);

        quad(x, y, w, h, dark, alphaMult);
        quad(x, y + h - thickness, w, thickness, light, alphaMult);
        quad(x, y, thickness, h, light, alphaMult);
        quad(x + thickness, y + thickness, w - thickness * 2f, h - thickness * 2f, base, alphaMult);
    }

    /** A hollow frame, drawn as four bars. */
    public static void frame(float x, float y, float w, float h, Color color,
                             float thickness, float alphaMult) {
        quad(x, y + h - thickness, w, thickness, color, alphaMult);
        quad(x, y, w, thickness, color, alphaMult);
        quad(x, y, thickness, h, color, alphaMult);
        quad(x + w - thickness, y, thickness, h, color, alphaMult);
    }

    /** Darkens the inside edges of a rectangle, so a reel window looks recessed. */
    public static void innerShadow(float x, float y, float w, float h, float thickness, float alphaMult) {
        Color shadow = new Color(0, 0, 0, 140);
        quad(x, y + h - thickness, w, thickness, shadow, alphaMult);
        quad(x, y, w, thickness * 0.6f, shadow, alphaMult * 0.6f);
        quad(x, y, thickness, h, shadow, alphaMult);
        quad(x + w - thickness, y, thickness, h, shadow, alphaMult * 0.6f);
    }

    public static void circle(float cx, float cy, float radius, Color color, float alphaMult, int segments) {
        setColor(color, alphaMult);
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex2f(cx, cy);
        for (int i = 0; i <= segments; i++) {
            double angle = i * 2.0 * Math.PI / segments;
            GL11.glVertex2f(cx + (float) Math.cos(angle) * radius, cy + (float) Math.sin(angle) * radius);
        }
        GL11.glEnd();
    }

    public static void line(float x1, float y1, float x2, float y2, Color color,
                            float width, float alphaMult) {
        setColor(color, alphaMult);
        GL11.glLineWidth(width);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2f(x1, y1);
        GL11.glVertex2f(x2, y2);
        GL11.glEnd();
        GL11.glLineWidth(1f);
    }

    /** A vertical fade, used to darken the top and bottom of a spinning reel. */
    public static void verticalFade(float x, float y, float w, float h,
                                    Color top, Color bottom, float alphaMult) {
        GL11.glBegin(GL11.GL_QUADS);
        setColor(bottom, alphaMult);
        GL11.glVertex2f(x, y);
        GL11.glVertex2f(x + w, y);
        setColor(top, alphaMult);
        GL11.glVertex2f(x + w, y + h);
        GL11.glVertex2f(x, y + h);
        GL11.glEnd();
    }
}
