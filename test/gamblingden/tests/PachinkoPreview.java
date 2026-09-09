package gamblingden.tests;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.imageio.ImageIO;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import com.fs.starfarer.api.*;
import com.fs.starfarer.api.ui.*;
import gamblingden.pachinko.*;
import gamblingden.economy.TokenBank;

/** Offscreen layout QA. Draws the real board; system fonts stand in for game label fonts. */
public final class PachinkoPreview {
    private static float previewScale=1;
    private static final List<Caption> captions = new ArrayList<>();
    private static final class Caption {
        String text, font;
        Color color = Color.WHITE;
        float x, y, w, h;
        Object label;
        PositionAPI position;
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<?>[] types, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(types[0].getClassLoader(), types, handler);
    }

    private static PositionAPI position(Caption c) {
        return proxy(new Class<?>[]{PositionAPI.class}, (p,m,a)->switch(m.getName()) {
            case "inTL" -> { c.x=(Float)a[0]; c.y=(Float)a[1]; yield p; }
            case "setSize" -> { c.w=(Float)a[0]; c.h=(Float)a[1]; yield null; }
            case "getX" -> c.x;
            case "getY" -> c.y;
            case "getWidth" -> c.w;
            case "getHeight" -> c.h;
            default -> null;
        });
    }

    private static Object label(String text, String font) {
        Caption c = new Caption(); c.text=text; c.font=font; c.position=position(c);
        c.label=proxy(new Class<?>[]{LabelAPI.class,UIComponentAPI.class}, (p,m,a)->switch(m.getName()) {
            case "setText" -> { c.text=(String)a[0]; yield null; }
            case "setColor" -> { c.color=(Color)a[0]; yield null; }
            case "getPosition" -> c.position;
            case "getText" -> c.text;
            default -> null;
        });
        captions.add(c); return c.label;
    }

    private static void save(com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin panel, String name) throws Exception {
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        panel.renderBelow(1);
        int width=Math.round(1000*previewScale),height=Math.round(660*previewScale);
        ByteBuffer pixels=BufferUtils.createByteBuffer(width*height*4);
        GL11.glReadPixels(0,0,width,height,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
        BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<height;y++) for(int x=0;x<width;x++) {
            int i=(y*width+x)*4;
            image.setRGB(x,height-1-y,0xff000000 | (pixels.get(i)&255)<<16 | (pixels.get(i+1)&255)<<8 | (pixels.get(i+2)&255));
        }
        Graphics2D g=image.createGraphics();
        g.scale(previewScale,previewScale);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        for(Caption c:captions) {
            boolean large=Fonts.ORBITRON_20AA.equals(c.font);
            g.setFont(new Font(Font.SANS_SERIF,large?Font.BOLD:Font.PLAIN,large?20:14));
            g.setColor(c.color);
            FontMetrics fm=g.getFontMetrics();
            if(fm.stringWidth(c.text)>c.w) throw new AssertionError("Label overflows: "+c.text);
            g.drawString(c.text,c.x+(c.w-fm.stringWidth(c.text))/2,c.y+fm.getAscent());
        }
        g.dispose();
        File output=new File("build/"+(panel instanceof PachinkoPanel?"pachinko-":panel instanceof gamblingden.jackpot.JackpotPanel?"jackpot-":"blackjack-")+name+(previewScale==1?"":"-"+previewScale+"x")+".png"); output.getParentFile().mkdirs();
        ImageIO.write(image,"png",output);
        System.out.println(output.getAbsolutePath());
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        Method setup=RegressionChecks.class.getDeclaredMethod("setup"); setup.setAccessible(true); setup.invoke(null);
        Field pool=RegressionChecks.class.getDeclaredField("hullmods"); pool.setAccessible(true);
        Method hullmod=RegressionChecks.class.getDeclaredMethod("hullmod",String.class,float.class,String[].class); hullmod.setAccessible(true);
        for(int i=0;i<120;i++) ((List<Object>)pool.get(null)).add(hullmod.invoke(null,"Blueprint "+i,1f,new String[0]));
        TokenBank.addTokens(240);
        SettingsAPI original=Global.getSettings();
        Global.setSettings(proxy(new Class<?>[]{SettingsAPI.class},(p,m,a)->m.getName().equals("createLabel")
                ? label((String)a[0],(String)a[1]) : m.invoke(original,a)));
        CustomPanelAPI ui=proxy(new Class<?>[]{CustomPanelAPI.class},(p,m,a)->{
            if(m.getName().equals("addComponent")) for(Caption c:captions) if(c.label==a[0]) return c.position;
            return null;
        });
        if(args.length>0 && args[0].equals("blackjack")) {
            if(args.length>1) previewScale=Float.parseFloat(args[1]);
            blackjack(ui);return;
        }
        if(args.length>0 && args[0].equals("jackpot")) { jackpot(ui); return; }
        boolean anime=args.length>0 && args[0].equals("anime");
        if(anime) {
            SettingsAPI base=Global.getSettings();
            var images=new java.util.HashMap<String,com.fs.starfarer.api.graphics.SpriteAPI>();
            Global.setSettings(proxy(new Class<?>[]{SettingsAPI.class},(p,m,a)->{
                if(m.getName().equals("getSprite") && a[0].equals(PachinkoBackdrop.IMAGE))
                    return images.computeIfAbsent((String)a[0],PachinkoPreview::sprite);
                return m.invoke(base,a);
            }));
        }
        PachinkoPanel panel=new PachinkoPanel(); panel.init(ui,null);
        if(anime) {
            Field backdrop=PachinkoPanel.class.getDeclaredField("backdrop");backdrop.setAccessible(true);
            ((PachinkoBackdrop)backdrop.get(panel)).init(true);
        }
        Caption bounds=new Caption(); bounds.w=1000; bounds.h=660; panel.positionChanged(position(bounds));
        Pbuffer buffer=new Pbuffer(1000,660,new PixelFormat(),null);
        try {
            buffer.makeCurrent(); GL11.glViewport(0,0,1000,660);
            GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glLoadIdentity(); GL11.glOrtho(0,1000,0,660,-1,1);
            GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glLoadIdentity();
            save(panel,"ready");
            Field rng=PachinkoPanel.class.getDeclaredField("random"); rng.setAccessible(true);
            long seed=0;
            for(;seed<10000;seed++) {
                PachinkoBoard b=new PachinkoBoard(new Random(new Random(seed).nextLong())); b.finish();
                if(b.getPocket()==1) break;
            }
            ((Random)rng.get(panel)).setSeed(seed);
            Method act=PachinkoPanel.class.getDeclaredMethod("act",String.class); act.setAccessible(true); act.invoke(panel,"drop");
            for(int frame=0;frame<90;frame++) panel.advance(1f/60);
            save(panel,"falling");
            act.invoke(panel,"skip"); save(panel,"won");
            act.invoke(panel,"drop50");
            for(int frame=0;frame<80;frame++) panel.advance(1f/60);
            save(panel,"multiball");
            act.invoke(panel,"skip"); save(panel,"batch-paid");
            act.invoke(panel,"category:CREDITS");save(panel,"credits-ready");
            TokenBank.addTokens(1000);act.invoke(panel,"drop50");
            for(int frame=0;frame<80;frame++) panel.advance(1f/60);
            save(panel,"credits-falling");act.invoke(panel,"skip");save(panel,"credits-paid");
        } finally { buffer.destroy(); }
    }

    @SuppressWarnings("unchecked")
    private static void blackjack(CustomPanelAPI ui) throws Exception {
        SettingsAPI original=Global.getSettings();
        var images=new java.util.HashMap<String,com.fs.starfarer.api.graphics.SpriteAPI>();
        Global.setSettings(proxy(new Class<?>[]{SettingsAPI.class},(p,m,a)->{
            if(m.getName().equals("getSprite") && ((String)a[0]).startsWith(gamblingden.blackjack.CardArt.ROOT))
                return images.computeIfAbsent((String)a[0],PachinkoPreview::sprite);
            return m.invoke(original,a);
        }));
        var panel=new gamblingden.blackjack.BlackjackPanel(); panel.init(ui,null);
        Caption bounds=new Caption(); bounds.w=1000; bounds.h=660; panel.positionChanged(position(bounds));
        Field gameField=panel.getClass().getDeclaredField("game"); gameField.setAccessible(true);
        var game=(gamblingden.blackjack.BlackjackGame)gameField.get(panel);
        Method act=panel.getClass().getDeclaredMethod("act",String.class); act.setAccessible(true);
        Pbuffer buffer=new Pbuffer(Math.round(1000*previewScale),Math.round(660*previewScale),new PixelFormat(),null);
        try {
            buffer.makeCurrent(); GL11.glViewport(0,0,Math.round(1000*previewScale),Math.round(660*previewScale));
            GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glLoadIdentity(); GL11.glOrtho(0,1000,0,660,-1,1);
            GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glLoadIdentity();
            save(panel,"ready");
            BlackjackChecks.rig(game,8,10,8,6,3,2,10,10,4);
            act.invoke(panel,"deal"); save(panel,"player");
            act.invoke(panel,"split"); save(panel,"split");
            act.invoke(panel,"double"); save(panel,"second-hand");
            act.invoke(panel,"double"); save(panel,"dealer");
            panel.advance(.5f); save(panel,"result");
            act.invoke(panel,"bet:20"); BlackjackChecks.rig(game,6,13,8,8,2,3);
            act.invoke(panel,"deal");act.invoke(panel,"hit");act.invoke(panel,"hit");act.invoke(panel,"stand");
            panel.advance(.5f);save(panel,"readable-hand");
            act.invoke(panel,"bet:20"); BlackjackChecks.rig(game,8,10,8,6,3,2);
            act.invoke(panel,"deal"); act.invoke(panel,"split");
            // Layout stress only: two twenty-card hands, without changing gameplay rules.
            Field cards=gamblingden.blackjack.BlackjackGame.Hand.class.getDeclaredField("cards"); cards.setAccessible(true);
            for(var hand:game.hands()) {
                var list=(List<gamblingden.blackjack.BlackjackGame.Card>)cards.get(hand); list.clear();
                for(int i=0;i<20;i++) list.add(new gamblingden.blackjack.BlackjackGame.Card(1,
                        gamblingden.blackjack.BlackjackGame.Suit.values()[i%4]));
            }
            Method refresh=panel.getClass().getDeclaredMethod("refresh"); refresh.setAccessible(true); refresh.invoke(panel);
            save(panel,"crowded");
            // Large face ranks: every rank/suit, without a shadow or UI font substitute.
            int rank=1;
            for(var hand:game.hands()) {
                var list=(List<gamblingden.blackjack.BlackjackGame.Card>)cards.get(hand);list.clear();
                for(int i=0;i<7 && rank<=13;i++,rank++) list.add(new gamblingden.blackjack.BlackjackGame.Card(rank,
                        gamblingden.blackjack.BlackjackGame.Suit.values()[rank%4]));
            }
            refresh.invoke(panel);save(panel,"ranks");
        } finally { buffer.destroy(); }
    }

    private static com.fs.starfarer.api.graphics.SpriteAPI sprite(String path) {
        String filename=path.substring(path.lastIndexOf('/')+1);
        String actual=switch(filename) {
            case "gamma_core.png" -> "ai_core_gamma.png";case "beta_core.png" -> "ai_core_beta.png";case "alpha_core.png" -> "ai_core_alpha.png";
            case "corrupted_nanoforge.png" -> "nanoforge_corrupted.png";case "pristine_nanoforge.png" -> "nanoforge_pristine.png";
            case "synchrotron.png" -> "synchrotron_core.png";case "orbital_fusion_lamp.png" -> "fusion_lamp.png";
            case "coronal_portal.png" -> "hypershunt_tap.png";case "mantle_bore.png" -> "terraforming_bore.png";
            case "drone_replicator.png" -> "combat_drone_replicator.png";case "dealmaker_holosuite.png" -> "holosuite.png";
            default -> filename;
        };
        float[] size={66,66},alpha={1};int[] texture={0};
        return proxy(new Class<?>[]{com.fs.starfarer.api.graphics.SpriteAPI.class},(p,m,a)->switch(m.getName()) {
            case "getTextureId" -> 1;
            case "setSize" -> { size[0]=(Float)a[0];size[1]=(Float)a[1];yield null; }
            case "setAlphaMult" -> { alpha[0]=(Float)a[0];yield null; }
            case "renderAtCenter" -> {
                if(texture[0]==0) {
                    BufferedImage art=ImageIO.read(new File(path.startsWith(gamblingden.blackjack.CardArt.ROOT) || path.equals(PachinkoBackdrop.IMAGE)?path:
                            "D:/Games/StarSector/starsector-core/graphics/icons/cargo/"+actual));
                    ByteBuffer pixels=BufferUtils.createByteBuffer(art.getWidth()*art.getHeight()*4);
                    for(int y=art.getHeight()-1;y>=0;y--) for(int x=0;x<art.getWidth();x++) {
                        int rgba=art.getRGB(x,y);pixels.put((byte)(rgba>>16)).put((byte)(rgba>>8)).put((byte)rgba).put((byte)(rgba>>24));
                    }
                    pixels.flip();texture[0]=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture[0]);
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR);
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_LINEAR);
                    GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA,art.getWidth(),art.getHeight(),0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
                }
                float x=(Float)a[0],y=(Float)a[1],w=size[0]/2,h=size[1]/2;
                GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture[0]);GL11.glColor4f(1,1,1,alpha[0]);
                GL11.glBegin(GL11.GL_QUADS);
                GL11.glTexCoord2f(0,0);GL11.glVertex2f(x-w,y-h);GL11.glTexCoord2f(1,0);GL11.glVertex2f(x+w,y-h);
                GL11.glTexCoord2f(1,1);GL11.glVertex2f(x+w,y+h);GL11.glTexCoord2f(0,1);GL11.glVertex2f(x-w,y+h);GL11.glEnd();
                GL11.glDisable(GL11.GL_BLEND); // Match Starsector's real Sprite renderer.
                yield null;
            }
            default -> null;
        });
    }
    private static void jackpot(CustomPanelAPI ui) throws Exception {
        Method defaults=RegressionChecks.class.getDeclaredMethod("jackpotDefaults");defaults.setAccessible(true);defaults.invoke(null);
        SettingsAPI original=Global.getSettings();
        var images=new java.util.HashMap<String,com.fs.starfarer.api.graphics.SpriteAPI>();
        Global.setSettings(proxy(new Class<?>[]{SettingsAPI.class},(p,m,a)->{
            if(m.getName().equals("getSprite")) return images.computeIfAbsent((String)a[0],PachinkoPreview::sprite);
            return m.invoke(original,a);
        }));
        var panel=new gamblingden.jackpot.JackpotPanel();panel.init(ui,null);
        Caption bounds=new Caption();bounds.w=1000;bounds.h=660;panel.positionChanged(position(bounds));
        Method act=panel.getClass().getDeclaredMethod("act",String.class);act.setAccessible(true);
        Field rng=panel.getClass().getDeclaredField("random");rng.setAccessible(true);
        Pbuffer buffer=new Pbuffer(1000,660,new PixelFormat(),null);
        try {
            buffer.makeCurrent();GL11.glViewport(0,0,1000,660);
            GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glLoadIdentity();GL11.glOrtho(0,1000,0,660,-1,1);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glLoadIdentity();
            // Mock names use actual vanilla art names where item IDs differ.
            save(panel,"ready");
            long win=0,lose=0;
            for(int seed=0;seed<10000;seed++) {
                TokenBank.addTokens(6);
                var round=gamblingden.jackpot.JackpotGame.buy(2,new Random(seed));
                if(round.winner()!=null) { win=seed;break; }
                lose=seed;
            }
            ((Random)rng.get(panel)).setSeed(win);act.invoke(panel,"pull");panel.advance(.5f);save(panel,"spinning");
            act.invoke(panel,"skip");save(panel,"win");
            ((Random)rng.get(panel)).setSeed(lose);act.invoke(panel,"pull");act.invoke(panel,"skip");save(panel,"loss");
        } finally { buffer.destroy(); }
    }
}
