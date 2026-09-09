package gamblingden.blackjack;

import java.awt.Color;
import java.util.*;
import org.lwjgl.opengl.GL11;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;
import gamblingden.blackjack.BlackjackGame.Card;
import gamblingden.blackjack.BlackjackGame.Suit;

/**
 * Full-card sprites, following Interastral Peace Casino's CardSprites/CardRenderingUtils.
 * The SVG-cards deck by David Bellot and contributors has separate LGPL-2.1 terms.
 * See THIRD_PARTY_NOTICES.txt and licenses/svg-cards/ for notices and original artwork.
 */
public final class CardArt {
    public static final String ROOT="graphics/gamblingden/cards/";
    public static final String BACK=ROOT+"back-red.png";
    public static final float HEIGHT_PER_WIDTH=490f/338f;
    private final Map<String,SpriteAPI> sprites=new HashMap<>();
    private boolean ready;

    public static String pathFor(Card card) {
        String suit=switch(card.suit()) {
            case SPADES -> "spade"; case HEARTS -> "heart";
            case DIAMONDS -> "diamond"; case CLUBS -> "club";
        };
        String rank=switch(card.rank()) {
            case 11 -> "jack";case 12 -> "queen";case 13 -> "king";
            default -> Integer.toString(card.rank());
        };
        return ROOT+suit+"_"+rank+".png";
    }
    public static List<String> paths() {
        List<String> paths=new ArrayList<>();paths.add(BACK);
        for(Suit suit:Suit.values()) for(int rank=1;rank<=13;rank++) paths.add(pathFor(new Card(rank,suit)));
        return List.copyOf(paths);
    }
    /** Load before play, never from the rendering callback. No external mod is required. */
    public void load() {
        sprites.clear();ready=true;
        for(String path:paths()) {
            try {
                Global.getSettings().loadTexture(path);
                SpriteAPI sprite=Global.getSettings().getSprite(path);
                if(sprite==null || sprite.getTextureId()==0) throw new IllegalStateException("Missing card: "+path);
                sprites.put(path,sprite);
            } catch(Exception e) {
                ready=false;
                Global.getLogger(CardArt.class).warn("Gambling Den: could not load card artwork "+path,e);
            }
        }
    }
    public boolean isReady() { return ready; }
    public void draw(String path,float left,float bottom,float width,float height,float alpha) {
        SpriteAPI sprite=sprites.get(path);
        if(sprite==null) return;
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1,1,1,1);
        sprite.setSize(width,height);sprite.setColor(Color.WHITE);sprite.setAlphaMult(alpha);
        sprite.setAngle(0);sprite.setNormalBlend();
        sprite.renderAtCenter(left+width/2,bottom+height/2);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
    }
}
