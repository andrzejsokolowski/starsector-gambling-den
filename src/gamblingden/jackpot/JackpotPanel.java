package gamblingden.jackpot;

import java.awt.Color;
import java.util.*;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomVisualDialogDelegate.DialogCallbacks;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.*;
import gamblingden.economy.TokenBank;
import gamblingden.jackpot.JackpotGame.Reward;
import gamblingden.ui.GLDraw;

/** Separate three-match cabinet. The final icons and receipt share the same Reward objects. */
public final class JackpotPanel extends BaseCustomUIPanelPlugin {
    public static final float PANEL_W=1000, PANEL_H=660;
    private static final Color BG=new Color(10,13,20), GOLD=new Color(235,185,100), DIM=new Color(150,157,173);
    private final Random random=new Random();
    private final List<Btn> buttons=new ArrayList<>();
    private final List<String> sessionLog=new ArrayList<>();
    private final Map<String,SpriteAPI> sprites=new HashMap<>();
    private final LabelAPI[] names=new LabelAPI[3];
    private CustomPanelAPI panel;
    private PositionAPI position;
    private DialogCallbacks callbacks;
    private LabelAPI bank,result,odds;
    private List<Reward> pool=List.of();
    private JackpotGame.Round round;
    private int stake=2;
    private float timer;
    private boolean spinning,dismissed,wasMouseDown;
    private static final class Btn {
        String action; float x,y,w,h; LabelAPI label; boolean enabled,checked,hover;
        Btn(String action,float x,float y,float w,float h) { this.action=action;this.x=x;this.y=y;this.w=w;this.h=h; }
        boolean contains(float x,float y) { return x>=this.x && x<=this.x+w && y>=this.y && y<=this.y+h; }
    }
    public void init(CustomPanelAPI panel,DialogCallbacks callbacks) {
        this.panel=panel;this.callbacks=callbacks;
        wasMouseDown=Mouse.isCreated() && Mouse.isButtonDown(0);
        bank=label("",Color.WHITE,0,18,1000,18,Fonts.DEFAULT_SMALL);
        label("RELIC JACKPOT",GOLD,0,107,1000,26,Fonts.ORBITRON_20AA);
        label("3 MATCHING SYMBOLS  |  1 ITEM",DIM,0,145,1000,18,Fonts.DEFAULT_SMALL);
        label("Per reel",DIM,264,51,82,18,Fonts.DEFAULT_SMALL);
        for(int i=0;i<3;i++) button("stake:"+new int[]{2,4,8}[i],new int[]{2,4,8}[i]+" tokens",354+i*100,44,90,32);
        for(int i=0;i<3;i++) names[i]=label("",GOLD,163+i*228,443,218,48,Fonts.DEFAULT_SMALL);
        result=label("",Color.WHITE,40,504,920,34,Fonts.DEFAULT_SMALL);
        odds=label("",DIM,0,550,1000,18,Fonts.DEFAULT_SMALL);
        button("pull","Pull",210,599,230,40);
        button("skip","Skip",458,599,150,40);
        button("leave","Leave",626,599,164,40);
        refreshPool(); refresh();
    }
    private LabelAPI label(String text,Color color,float x,float y,float w,float h,String font) {
        LabelAPI label=Global.getSettings().createLabel(text,font); label.setColor(color);label.setAlignment(Alignment.MID);
        panel.addComponent((UIComponentAPI)label).inTL(x,y).setSize(w,h); return label;
    }
    private void button(String action,String text,float x,float y,float w,float h) {
        Btn b=new Btn(action,x,y,w,h); b.label=label(text,Color.WHITE,x,y+(h-18)/2,w,18,Fonts.DEFAULT_SMALL); buttons.add(b);
    }
    private void refreshPool() {
        pool=JackpotGame.pool(stake);
        // Load once outside the render loop, including art from optional installed mods.
        for(Reward r:pool) if(r.icon()!=null && !sprites.containsKey(r.icon())) {
            SpriteAPI sprite=null;
            try { Global.getSettings().loadTexture(r.icon()); sprite=Global.getSettings().getSprite(r.icon()); }
            catch(Exception ignored) { }
            sprites.put(r.icon(),sprite);
        }
    }
    private void refresh() {
        bank.setText(TokenBank.getTokens()+" tokens");
        odds.setText(pool.isEmpty()?"No eligible rewards":"Match chance: "+String.format(Locale.ROOT,"%.2f%%",JackpotGame.matchChance(stake)*100));
        for(Btn b:buttons) {
            b.checked=b.action.equals("stake:"+stake);
            b.enabled=b.action.startsWith("stake:")?!spinning:switch(b.action) {
                case "pull" -> !spinning && !pool.isEmpty() && TokenBank.getTokens()>=JackpotGame.costOf(stake);
                case "skip" -> spinning; default -> true;
            };
            if(b.action.equals("pull")) b.label.setText("Pull - "+JackpotGame.costOf(stake)+" tokens");
            b.label.setColor(b.enabled?Color.WHITE:DIM.darker());
        }
    }
    private void act(String action) {
        if(dismissed) return;
        if(action.equals("leave")) { finishOnDismissal();if(callbacks!=null) callbacks.dismissDialog();return; }
        if(action.equals("skip")) { if(spinning) settle(); return; }
        if(spinning) return;
        if(action.startsWith("stake:")) {
            int next=JackpotGame.clampStake(Integer.parseInt(action.substring(6)));
            if(next==stake) return;
            stake=next;round=null;timer=0;result.setText("");
            for(LabelAPI name:names) name.setText("");
            refreshPool();refresh();
        } else if(action.equals("pull")) {
            var next=JackpotGame.buy(stake,random);
            if(next==null) { refreshPool();refresh();return; }
            round=next;spinning=true;timer=0;result.setText("");
            for(LabelAPI name:names) name.setText("");
            Global.getSoundPlayer().playUISound("ui_button_pressed",1,.6f);refresh();
        }
    }
    private void settle() {
        if(!spinning || round==null) return;
        String message=round.finish();
        spinning=false;timer=3;
        for(int i=0;i<3;i++) names[i].setText(round.symbols.get(i)==null?"-":round.symbols.get(i).name());
        result.setText(message);result.setColor(round.winner()==null?DIM:GOLD);
        if(round.winner()!=null) sessionLog.add(message);
        Global.getSoundPlayer().playUISound(round.winner()==null?"ui_button_pressed":"ui_chip_pickup",1,.6f);
        refresh();
    }
    public void finishOnDismissal() {
        if(dismissed) return;
        settle();dismissed=true;
    }
    public List<String> getSessionLog() { return new ArrayList<>(sessionLog); }
    private void pointer(float mx,float my,boolean down) {
        boolean click=down&&!wasMouseDown;wasMouseDown=down;
        for(Btn b:buttons) b.hover=b.enabled&&b.contains(mx,my);
        if(!click||dismissed) return;
        for(Btn b:buttons) if(b.contains(mx,my)) { if(b.enabled) act(b.action);return; }
    }
    @Override public void advance(float amount) {
        if(dismissed) return;
        if(position!=null && Mouse.isCreated()) {
            float scale=Global.getSettings().getScreenScaleMult();
            if(scale>0) pointer(Mouse.getX()/scale-position.getX(),position.getY()+PANEL_H-Mouse.getY()/scale,Mouse.isButtonDown(0));
        }
        if(spinning && Float.isFinite(amount) && amount>0) {
            timer+=amount;
            for(int i=0;i<3;i++) if(timer>=stopTime(i)) names[i].setText(round.symbols.get(i)==null?"-":round.symbols.get(i).name());
            if(timer>=stopTime(2)+.2f) settle();
        }
    }
    @Override public void processInput(List<InputEventAPI> events) {
        if(events==null || dismissed) return;
        for(InputEventAPI e:events) if(!e.isConsumed() && e.isKeyDownEvent()) {
            if(e.getEventValue()==Keyboard.KEY_ESCAPE) { e.consume();act("leave");return; }
            if(e.getEventValue()==Keyboard.KEY_SPACE && !spinning) { e.consume();act("pull"); }
        }
    }
    private static float stopTime(int reel) { return 1.1f+reel*.5f; }
    @Override public void positionChanged(PositionAPI p) { position=p; }
    private float x(float n) { return position.getX()+n; }
    private float y(float n) { return position.getY()+PANEL_H-n; }
    private void rect(float x,float y,float w,float h,Color c,float a) { GLDraw.quad(x(x),y(y+h),w,h,c,a); }
    @Override public void renderBelow(float alpha) {
        if(position==null) return;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT|GL11.GL_COLOR_BUFFER_BIT|GL11.GL_SCISSOR_BIT|GL11.GL_CURRENT_BIT|GL11.GL_TEXTURE_BIT|GL11.GL_LINE_BIT);
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
            rect(0,0,1000,660,BG,alpha);
            GLDraw.bevelledPanel(x(140),y(494),720,400,new Color(46,39,43),6,alpha);
            GLDraw.frame(x(140),y(494),720,400,GOLD,2,alpha*.7f);
            for(int i=0;i<3;i++) drawReel(i,alpha);
            for(Btn b:buttons) {
                Color fill=!b.enabled?new Color(22,26,33):b.checked?new Color(98,76,38):b.hover?new Color(60,67,78):new Color(32,40,50);
                GLDraw.bevelledPanel(x(b.x),y(b.y+b.h),b.w,b.h,fill,3,alpha);
                GLDraw.frame(x(b.x),y(b.y+b.h),b.w,b.h,b.checked?GOLD:DIM,1,alpha*(b.enabled?.65f:.2f));
            }
        } finally { GL11.glPopAttrib(); }
    }
    private void drawReel(int reel,float alpha) {
        float left=190+228*reel,top=189,w=164,h=246;
        rect(left,top,w,h,new Color(13,17,25),alpha);
        boolean stopped=round!=null && (!spinning || timer>=stopTime(reel));
        GL11.glPushAttrib(GL11.GL_SCISSOR_BIT|GL11.GL_ENABLE_BIT);
        try {
            float scale=Global.getSettings().getScreenScaleMult();
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor((int)(x(left)*scale),(int)(y(top+h)*scale),(int)(w*scale),(int)(h*scale));
            if(stopped) {
                drawSymbol(round.symbols.get(reel),left+w/2,top+h/2,alpha);
            } else {
                float travel=spinning?timer*(550+reel*55):0, offset=travel%82;
                int step=(int)(travel/82);
                for(int row=-1;row<4;row++) {
                    Reward r=pool.isEmpty()?null:pool.get(Math.floorMod(step+row+reel*3,pool.size()));
                    drawSymbol(r,left+w/2,top+row*82+41+offset,alpha*(row==1?1:.3f));
                }
            }
        } finally { GL11.glPopAttrib(); }
        GLDraw.frame(x(left),y(top+h),w,h,GOLD,2,alpha*.65f);
        GLDraw.frame(x(left+2),y(top+164),w-4,82,GOLD,1,alpha*.55f);
    }
    private void drawSymbol(Reward reward,float cx,float cy,float alpha) {
        SpriteAPI sprite=reward==null?null:sprites.get(reward.icon());
        if(sprite!=null && sprite.getTextureId()!=0) {
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            sprite.setSize(66,66);sprite.setColor(Color.WHITE);sprite.setAlphaMult(alpha);sprite.setNormalBlend();sprite.renderAtCenter(x(cx),y(cy));
            GL11.glDisable(GL11.GL_TEXTURE_2D);
        } else {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            if(reward==null) GLDraw.line(x(cx-16),y(cy),x(cx+16),y(cy),DIM,3,alpha);
            else GLDraw.frame(x(cx-24),y(cy+24),48,48,GOLD,3,alpha);
        }
    }
}
