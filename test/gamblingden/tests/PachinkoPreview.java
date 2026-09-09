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

    private static void save(PachinkoPanel panel, String name) throws Exception {
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        panel.renderBelow(1);
        ByteBuffer pixels=BufferUtils.createByteBuffer(1000*660*4);
        GL11.glReadPixels(0,0,1000,660,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
        BufferedImage image=new BufferedImage(1000,660,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<660;y++) for(int x=0;x<1000;x++) {
            int i=(y*1000+x)*4;
            image.setRGB(x,659-y,0xff000000 | (pixels.get(i)&255)<<16 | (pixels.get(i+1)&255)<<8 | (pixels.get(i+2)&255));
        }
        Graphics2D g=image.createGraphics();
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
        File output=new File("build/pachinko-"+name+".png"); output.getParentFile().mkdirs();
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
        PachinkoPanel panel=new PachinkoPanel(); panel.init(ui,null);
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
        } finally { buffer.destroy(); }
    }
}
