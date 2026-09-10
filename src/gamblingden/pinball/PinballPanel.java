package gamblingden.pinball;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomVisualDialogDelegate.DialogCallbacks;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.*;
import gamblingden.economy.TokenBank;
import gamblingden.ui.GLDraw;

/** Mouse-first arcade table. The rendered ball and paddles are the scoring simulation. */
public final class PinballPanel extends BaseCustomUIPanelPlugin {
    public static final float PANEL_W=1000,PANEL_H=660;
    private static final float TABLE_X=270,TABLE_Y=72;
    private static final Color BG=new Color(9,15,23),BOARD=new Color(16,29,39),GOLD=new Color(245,198,103),
            CYAN=new Color(93,201,218),DIM=new Color(139,158,173),RED=new Color(239,112,120);
    private static final class Btn {
        String action;float x,y,w,h;LabelAPI label;boolean enabled,checked,hover;
        Btn(String action,float x,float y,float w,float h) { this.action=action;this.x=x;this.y=y;this.w=w;this.h=h; }
        boolean contains(float x,float y) { return x>=this.x && x<=this.x+w && y>=this.y && y<=this.y+h; }
    }
    private final Random random=new Random();
    private final List<Btn> buttons=new ArrayList<>();
    private final List<String> sessionLog=new ArrayList<>();
    private final LabelAPI[] tiers=new LabelAPI[4],rewards=new LabelAPI[4],bumperValues=new LabelAPI[3];
    private final PinballBoard idle=new PinballBoard(new Random(1));
    private CustomPanelAPI panel;
    private PositionAPI position;
    private DialogCallbacks callbacks;
    private LabelAPI bank,score,ball,status,collected;
    private PinballSettings.Category category=PinballSettings.Category.TOKENS;
    private PinballSettings.Offer quote;
    private PinballGame game;
    private boolean dismissed,wasLeftDown,mouseLeft,mouseRight,keyLeft,keyRight,spaceDown,recorded;
    private int lastSoundScore;
    private float soundCooldown;
    public void init(CustomPanelAPI panel,DialogCallbacks callbacks) {
        this.panel=panel;this.callbacks=callbacks;
        wasLeftDown=Mouse.isCreated()&&Mouse.isButtonDown(0);
        label("PINBALL",GOLD,0,20,PANEL_W,25,Fonts.ORBITRON_20AA);
        bank=label("",Color.WHITE,30,78,218,20,Fonts.DEFAULT_SMALL);
        int i=0;
        for(var value:PinballSettings.Category.values())
            button("category:"+value.name(),value.label,34,122+i++*38,210,30);
        button("play","Play",34,337,210,40);
        button("nudge","Nudge",34,391,210,38);
        button("drain","Drain ball",34,443,210,38);
        label("NUDGE HEAT",DIM,34,487,210,18,Fonts.DEFAULT_SMALL);
        label("CONTROLS",CYAN,20,539,236,18,Fonts.DEFAULT_SMALL);
        String[] controls={"Left: left mouse / Left arrow","Right: right mouse / Right arrow",
                "Space: play / launch","Nudge: push ball inward","Rapid nudges cause tilt"};
        for(i=0;i<controls.length;i++) label(controls[i],DIM,20,561+i*18,236,18,Fonts.DEFAULT_SMALL);
        score=label("0",GOLD,754,84,212,28,Fonts.ORBITRON_20AA);
        ball=label("",DIM,754,124,212,20,Fonts.DEFAULT_SMALL);
        label("PRIZES",CYAN,754,169,212,20,Fonts.DEFAULT_SMALL);
        for(i=0;i<4;i++) {
            tiers[i]=label("",DIM,754,206+i*50,212,18,Fonts.DEFAULT_SMALL);
            rewards[i]=label("",Color.WHITE,754,224+i*50,212,18,Fonts.DEFAULT_SMALL);
        }
        status=label("",GOLD,754,434,212,25,Fonts.ORBITRON_20AA);
        collected=label("",Color.WHITE,744,474,230,25,Fonts.DEFAULT_SMALL);
        button("collect","End & collect",754,522,212,38);
        button("leave","Leave",754,573,212,38);
        button("left","Left flipper / LMB",270,616,220,32);
        button("right","Right flipper / RMB",510,616,220,32);
        label("LOOP",CYAN,TABLE_X+34,TABLE_Y+68,70,18,Fonts.DEFAULT_SMALL);
        for(i=0;i<3;i++) {
            var b=PinballBoard.BUMPERS.get(i);
            bumperValues[i]=label("100",Color.WHITE,TABLE_X+b.x()-22,TABLE_Y+b.y()-8,44,18,Fonts.DEFAULT_SMALL);
        }
        quote=PinballSettings.quote(category);refresh();
    }
    private LabelAPI label(String text,Color color,float x,float y,float w,float h,String font) {
        var label=Global.getSettings().createLabel(text,font);label.setColor(color);label.setAlignment(Alignment.MID);
        panel.addComponent((UIComponentAPI)label).inTL(x,y).setSize(w,h);return label;
    }
    private void button(String action,String text,float x,float y,float w,float h) {
        var b=new Btn(action,x,y,w,h);b.label=label(text,Color.WHITE,x,y+(h-18)/2,w,18,Fonts.DEFAULT_SMALL);buttons.add(b);
    }
    private boolean inRound() { return game!=null&&!game.finished(); }
    private PinballBoard board() { return game==null?idle:game.board(); }
    private void refresh() {
        var current=inRound()?game.offer:quote;
        int points=game==null?0:game.score(),tier=current.tier(points);
        bank.setText(TokenBank.getTokens()+" tokens");score.setText(PinballSettings.group(points));
        ball.setText(game==null?"3 balls":game.finished()?"Round complete":"Ball "+game.ballNumber()+" / 3");
        for(int i=0;i<4;i++) {
            tiers[i].setText(PinballSettings.group(current.scoreAt(i))+" points");
            rewards[i].setText(current.category.amountText(current.amountAt(i)));
            rewards[i].setColor(i==tier?GOLD:Color.WHITE);
        }
        var b=board();
        status.setText(game!=null&&game.finished()?"":b.tilted()?"TILT":inRound()&&b.ready()?"LAUNCH":
                b.playing()?b.multiplier()+"x":"");
        status.setColor(b.tilted()?RED:GOLD);
        for(var label:bumperValues) label.setText(Integer.toString(100*b.multiplier()));
        if(game!=null&&game.finished()&&!recorded) {
            recorded=true;collected.setText(game.receipt().describe());
            sessionLog.add("Pinball: "+PinballSettings.group(game.score())+" points.");
            sessionLog.addAll(game.receipt().getLines());
            Global.getSoundPlayer().playUISound(game.offer.awardAt(game.score())>0?"ui_chip_pickup":"ui_button_pressed",1,.6f);
        }
        for(Btn button:buttons) {
            String a=button.action;
            button.checked=a.equals("category:"+category.name()) || a.equals("left")&&(mouseLeft||keyLeft)
                    || a.equals("right")&&(mouseRight||keyRight);
            button.enabled=a.startsWith("category:")?!inRound():switch(a) {
                case "play" -> inRound()?b.ready():quote.available()&&TokenBank.getTokens()>=quote.cost;
                case "nudge" -> inRound()&&b.canNudge();
                case "drain","collect" -> inRound();
                case "left","right" -> inRound()&&b.playing()&&!b.tilted();
                default -> true;
            };
            if(a.equals("play")) button.label.setText(inRound()?"Launch ball":"Play - "+quote.cost+" tokens");
            if(a.equals("leave")) button.label.setText(inRound()?"End & leave":"Leave");
            button.label.setColor(button.enabled?Color.WHITE:DIM.darker());
        }
    }
    private void act(String action) {
        if(dismissed) return;
        if(action.equals("leave")) { finishOnDismissal();if(callbacks!=null) callbacks.dismissDialog();return; }
        if(action.startsWith("category:")&&!inRound()) {
            for(var choice:PinballSettings.Category.values()) if(action.equals("category:"+choice.name()) && choice!=category) {
                category=choice;game=null;recorded=false;collected.setText("");quote=PinballSettings.quote(category);
            }
        } else if(action.equals("play")) {
            if(inRound()) {
                if(game.launch()) Global.getSoundPlayer().playUISound("ui_button_pressed",1,.5f);
            }
            else {
                var next=PinballGame.buy(quote,random);
                if(next!=null) {
                    game=next;recorded=false;lastSoundScore=0;collected.setText("");
                    Global.getSoundPlayer().playUISound("ui_chip_pickup",1,.5f);
                }
                else quote=PinballSettings.quote(category);
            }
        } else if(inRound()) switch(action) {
            case "nudge" -> game.nudge();
            case "drain" -> game.drainBall();
            case "collect" -> game.finish();
        };
        refresh();
    }
    /** Both mouse buttons can be held at once; each paddle also has a left-clickable button. */
    private void pointer(float x,float y,boolean leftDown,boolean rightDown) {
        boolean click=leftDown&&!wasLeftDown;wasLeftDown=leftDown;
        boolean table=x>=TABLE_X&&x<=TABLE_X+PinballBoard.WIDTH&&y>=TABLE_Y&&y<=TABLE_Y+PinballBoard.HEIGHT;
        mouseLeft=leftDown&&table;mouseRight=rightDown;
        for(Btn b:buttons) {
            b.hover=b.enabled&&b.contains(x,y);
            if(leftDown&&b.contains(x,y)) {
                if(b.action.equals("left")) mouseLeft=true;
                else if(b.action.equals("right")) mouseRight=true;
                else if(click&&b.enabled) { act(b.action);break; }
            }
        }
    }
    @Override public void advance(float amount) {
        if(dismissed) return;
        if(position!=null&&Mouse.isCreated()) {
            float scale=Global.getSettings().getScreenScaleMult();
            if(scale>0) pointer(Mouse.getX()/scale-position.getX(),position.getY()+PANEL_H-Mouse.getY()/scale,
                    Mouse.isButtonDown(0),Mouse.isButtonDown(1));
        }
        if(Float.isFinite(amount)&&amount>0) soundCooldown=Math.max(0,soundCooldown-amount);
        if(inRound()) {
            game.advance(amount,mouseLeft||keyLeft,mouseRight||keyRight);
            if(!game.finished()&&game.score()>lastSoundScore&&soundCooldown<=0) {
                Global.getSoundPlayer().playUISound("ui_button_pressed",1.1f+board().multiplier()*.08f,.3f);
                soundCooldown=.09f;
            }
            lastSoundScore=game.score();
        }
        refresh();
    }
    @Override public void processInput(List<InputEventAPI> events) {
        if(events==null||dismissed) return;
        for(var e:events) if(!e.isConsumed()) {
            if(e.isKeyDownEvent()) {
                switch(e.getEventValue()) {
                    case Keyboard.KEY_ESCAPE -> { e.consume();act("leave");return; }
                    case Keyboard.KEY_LEFT -> { keyLeft=true;e.consume(); }
                    case Keyboard.KEY_RIGHT -> { keyRight=true;e.consume(); }
                    case Keyboard.KEY_SPACE -> { if(!spaceDown) act("play");spaceDown=true;e.consume(); }
                }
            } else if(e.isKeyUpEvent()) {
                // Read once: Starsector rejects getEventValue() after consume().
                switch(e.getEventValue()) {
                    case Keyboard.KEY_LEFT -> { keyLeft=false;e.consume(); }
                    case Keyboard.KEY_RIGHT -> { keyRight=false;e.consume(); }
                    case Keyboard.KEY_SPACE -> { spaceDown=false;e.consume(); }
                }
            }
        }
    }
    public void finishOnDismissal() {
        if(dismissed) return;
        if(inRound()) game.finish();
        mouseLeft=mouseRight=keyLeft=keyRight=false;refresh();dismissed=true;
    }
    public List<String> getSessionLog() { return new ArrayList<>(sessionLog); }
    @Override public void positionChanged(PositionAPI p) { position=p; }
    private float x(float v) { return position.getX()+v; }
    private float y(float v) { return position.getY()+PANEL_H-v; }
    private void rect(float x,float y,float w,float h,Color c,float a) { GLDraw.quad(x(x),y(y+h),w,h,c,a); }
    private void circle(float px,float py,float r,Color c,float a) { GLDraw.circle(x(TABLE_X+px),y(TABLE_Y+py),r,c,a,24); }
    private void rail(PinballBoard.Rail rail,float width,Color color,float alpha) {
        GLDraw.line(x(TABLE_X+rail.x1()),y(TABLE_Y+rail.y1()),x(TABLE_X+rail.x2()),y(TABLE_Y+rail.y2()),color,width,alpha);
        circle(rail.x1(),rail.y1(),width/2,color,alpha);circle(rail.x2(),rail.y2(),width/2,color,alpha);
    }
    @Override public void renderBelow(float alpha) {
        if(position==null) return;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT|GL11.GL_COLOR_BUFFER_BIT|GL11.GL_SCISSOR_BIT|GL11.GL_CURRENT_BIT|GL11.GL_TEXTURE_BIT|GL11.GL_LINE_BIT);
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
            rect(0,0,PANEL_W,PANEL_H,BG,alpha);
            GLDraw.bevelledPanel(x(TABLE_X-8),y(TABLE_Y+PinballBoard.HEIGHT+8),PinballBoard.WIDTH+16,PinballBoard.HEIGHT+16,
                    new Color(32,54,66),6,alpha);
            rect(TABLE_X,TABLE_Y,PinballBoard.WIDTH,PinballBoard.HEIGHT,BOARD,alpha);
            for(int row=0;row<13;row++) for(int col=0;col<11;col++)
                circle(24+col*40,24+row*39,1,CYAN,alpha*.11f);
            for(int i=0;i<PinballBoard.RAILS.size();i++) rail(PinballBoard.RAILS.get(i),4,i>=10?CYAN:DIM,alpha*.85f);
            for(var sling:PinballBoard.SLINGS) rail(sling,6,RED,alpha*.8f);
            var ball=board();
            if(ball.shooterGateClosed()) rail(PinballBoard.SHOOTER_GATE,4,CYAN,alpha);
            circle(PinballBoard.SHOOTER_GATE.x1(),PinballBoard.SHOOTER_GATE.y1(),4,GOLD,alpha);
            for(int i=0;i<3;i++) {
                var b=PinballBoard.BUMPERS.get(i);float glow=ball.bumperFlash(i);
                circle(b.x(),b.y(),b.radius()+8,CYAN,alpha*(.12f+.25f*glow));
                circle(b.x(),b.y(),b.radius()+2,GOLD,alpha);
                circle(b.x(),b.y(),b.radius()-3,new Color(32,91,109),alpha);
                circle(b.x(),b.y(),b.radius()-8,CYAN,alpha*(.25f+.5f*glow));
                var t=PinballBoard.TARGETS.get(i);
                circle(t.x(),t.y(),t.radius()+5,GOLD,alpha*.15f);
                circle(t.x(),t.y(),t.radius(),(ball.targetMask()&(1<<i))!=0?GOLD:RED,alpha);
            }
            for(boolean left:new boolean[]{true,false}) {
                var f=ball.flipperRail(left);
                rail(f,PinballBoard.FLIPPER_RADIUS*2+4,new Color(6,11,18),alpha);
                rail(f,PinballBoard.FLIPPER_RADIUS*2,ball.tilted()?DIM:GOLD,alpha);
                circle(f.x1(),f.y1(),5,Color.WHITE,alpha*.8f);
            }
            // No GL queries: the same draw path is safe with the asynchronous renderer.
            float bx=ball.x(),by=Math.min(PinballBoard.HEIGHT-8,ball.y());
            circle(bx+2,by+3,PinballBoard.BALL_RADIUS+1,Color.BLACK,alpha*.5f);
            circle(bx,by,PinballBoard.BALL_RADIUS,new Color(192,212,225),alpha);
            circle(bx-2,by-2,2.5f,Color.WHITE,alpha);
            for(int i=0;i<3;i++) {
                float lit=Math.max(0,Math.min(1,ball.heat()-i));
                GLDraw.circle(x(105+i*35),y(516),9,lit>0?RED:DIM,alpha*(.2f+.8f*lit),20);
            }
            for(Btn b:buttons) {
                Color fill=!b.enabled?new Color(20,26,32):b.checked?new Color(40,93,105):b.hover?new Color(58,73,83):new Color(31,44,55);
                GLDraw.bevelledPanel(x(b.x),y(b.y+b.h),b.w,b.h,fill,2,alpha);
                GLDraw.frame(x(b.x),y(b.y+b.h),b.w,b.h,b.checked?CYAN:DIM,1,alpha*(b.enabled?.8f:.2f));
            }
        } finally { GL11.glPopAttrib(); }
    }
}
