package gamblingden.pachinko;

import java.awt.Color;
import org.lwjgl.opengl.GL11;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;

/** Optional artwork only. It has no access to ball physics, random draws, or rewards. */
public final class PachinkoBackdrop {
    public static final String IMAGE="graphics/gamblingden/pachinko/anime-background.png";
    private SpriteAPI sprite;
    public void init(boolean enabled) {
        sprite=null;
        if(!enabled) return;
        try {
            Global.getSettings().loadTexture(IMAGE);
            SpriteAPI loaded=Global.getSettings().getSprite(IMAGE);
            if(loaded!=null && loaded.getTextureId()!=0) sprite=loaded;
        } catch(Exception e) {
            Global.getLogger(PachinkoBackdrop.class).warn("Gambling Den: anime background unavailable; using the standard board.",e);
        }
    }
    public boolean active() { return sprite!=null; }
    public void draw(float x,float y,float w,float h,float alpha) {
        if(sprite==null) return;
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        sprite.setSize(w,h);sprite.setColor(Color.WHITE);sprite.setAlphaMult(alpha);
        sprite.setAngle(0);sprite.setNormalBlend();sprite.renderAtCenter(x+w/2,y+h/2);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
    }
}
