package gamblingden.blackjack;

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
import gamblingden.blackjack.BlackjackGame.*;
import gamblingden.economy.TokenBank;
import gamblingden.ui.GLDraw;

/** Original, mouse-first table and geometric card art. No casino assets or runtime dependency. */
public final class BlackjackPanel extends BaseCustomUIPanelPlugin {
    public static final float PANEL_W = 1000, PANEL_H = 660;
    private static final Color BG = new Color(10, 15, 23), FELT = new Color(19, 53, 48);
    private static final Color GOLD = new Color(255, 205, 105), DIM = new Color(145, 165, 165);
    private static final Color INK = new Color(28, 33, 44), RED = new Color(182, 48, 52);
    private final BlackjackGame game = new BlackjackGame(new Account() {
        public int balance() { return TokenBank.getTokens(); }
        public boolean take(int amount) { return TokenBank.spendTokens(amount); }
        public int pay(int amount) { return TokenBank.addTokens(amount); }
    }, new Random());
    private final List<Btn> buttons = new ArrayList<Btn>();
    private final Face[][] faces = new Face[3][22];
    private final LabelAPI[] handLabels = new LabelAPI[2];
    private CustomPanelAPI panel;
    private DialogCallbacks callbacks;
    private PositionAPI position;
    private LabelAPI bank, dealerLabel, result, betLabel;
    private int bet = 10;
    private boolean dismissed, wasMouseDown;
    private float dealerTimer;
    private long reportedRounds;

    private static final class Btn {
        final String action; final float x, y, w, h;
        LabelAPI label; boolean enabled, checked, hovered;
        Btn(String action, float x, float y, float w, float h) { this.action=action; this.x=x; this.y=y; this.w=w; this.h=h; }
        boolean contains(float x, float y) { return x>=this.x && x<=this.x+w && y>=this.y && y<=this.y+h; }
    }
    private static final class Face {
        String rankText = "";
        Card card;
        boolean visible, hidden;
        float x, y, w, h;
    }
    public void init(CustomPanelAPI panel, DialogCallbacks callbacks) {
        this.panel=panel; this.callbacks=callbacks;
        wasMouseDown=Mouse.isCreated() && Mouse.isButtonDown(0);
        label("BLACKJACK",GOLD,0,18,PANEL_W,26,Fonts.ORBITRON_20AA);
        bank=label("",Color.WHITE,0,48,PANEL_W,18,Fonts.DEFAULT_SMALL);
        label("BLACKJACK PAYS 3:2   |   DEALER STANDS ON SOFT 17",DIM,0,75,PANEL_W,18,Fonts.DEFAULT_SMALL);
        dealerLabel=label("",DIM,50,101,900,20,Fonts.DEFAULT_SMALL);
        for(int group=0;group<3;group++) for(int i=0;i<22;i++) {
            faces[group][i]=new Face();
        }
        for(int i=0;i<2;i++) handLabels[i]=label("",GOLD,0,318,900,20,Fonts.DEFAULT_SMALL);
        result=label("",GOLD,28,516,944,20,Fonts.DEFAULT_SMALL);
        betLabel=label("",DIM,0,540,PANEL_W,18,Fonts.DEFAULT_SMALL);
        String[] bets={"-2","2","4","10","20","50","100","+2","x2"};
        for(int i=0;i<bets.length;i++) button("bet:"+bets[i],bets[i],64+i*98,564,88,30);
        String[] actions={"deal","hit","stand","double","split","leave"};
        String[] names={"Deal","Hit","Stand","Double","Split","Leave"};
        for(int i=0;i<actions.length;i++) button(actions[i],names[i],38+i*156,608,144,36);
        refresh();
    }
    private LabelAPI label(String text, Color color, float x, float y, float w, float h, String font) {
        LabelAPI label=Global.getSettings().createLabel(text,font);
        label.setColor(color); label.setAlignment(Alignment.MID);
        panel.addComponent((UIComponentAPI)label).inTL(x,y).setSize(w,h);
        return label;
    }
    private void button(String action, String text, float x, float y, float w, float h) {
        Btn b=new Btn(action,x,y,w,h);
        b.label=label(text,Color.WHITE,x,y+(h-18)/2,w,18,Fonts.DEFAULT_SMALL); buttons.add(b);
    }
    private int proposedBet(String action) {
        return switch(action.substring(4)) {
            case "-2" -> bet-2; case "+2" -> bet+2; case "x2" -> bet*2;
            default -> Integer.parseInt(action.substring(4));
        };
    }
    private String handText(Hand hand, int index) {
        return (game.hands().size()==1 ? "You" : "Hand "+(index+1)) + " - " + hand.value()
                + (hand.soft()?" (soft)":"") + "  |  Bet " + hand.bet()
                + (hand.outcome().isEmpty()?"":"  |  "+hand.outcome());
    }
    private void refresh() {
        bank.setText(TokenBank.getTokens()+" tokens" + (game.playing()?"  |  On table: "+game.invested():""));
        dealerLabel.setText("Dealer"+(game.dealer().cards().isEmpty() || !game.holeRevealed()?"":" - "+game.dealer().value()
                +(game.dealer().soft()?" (soft)":"")));
        result.setText(game.state()==State.DEALER ? "Dealer's turn" : game.result());
        result.setColor(game.state()==State.RESULT && game.paid()<game.invested() ? new Color(239,125,113) : GOLD);
        betLabel.setText("Bet: "+bet+" tokens"+(!game.playing() && !game.canDeal(bet)
                ? (TokenBank.getTokens()<bet?"  |  Not enough tokens":"  |  Token balance limit") : ""));
        arrange(0,game.dealer().cards(),50,128,900,!game.holeRevealed());
        boolean split=game.hands().size()==2;
        for(int i=0;i<2;i++) {
            float left=split?50+i*468:50, width=split?432:900;
            handLabels[i].getPosition().inTL(left,318).setSize(width,20);
            handLabels[i].setText(i<game.hands().size()?handText(game.hands().get(i),i):i==0?"You":"");
            handLabels[i].setColor(game.state()==State.PLAYER && game.active()==i ? GOLD : DIM);
            arrange(i+1,i<game.hands().size()?game.hands().get(i).cards():List.of(),left,350,width,false);
            if(i==1 && !split) faces[2][0].visible=false;
        }
        for(Btn b:buttons) {
            b.checked=b.action.equals("bet:"+bet);
            if(b.action.startsWith("bet:")) { int next=proposedBet(b.action); b.enabled=!game.playing() && next>=2 && next<=1000; }
            else b.enabled=switch(b.action) {
                case "deal" -> game.canDeal(bet);
                case "hit","stand" -> game.state()==State.PLAYER;
                case "double" -> game.canDouble(); case "split" -> game.canSplit(); default -> true;
            };
            if(b.action.equals("deal")) b.label.setText("Deal - "+bet);
            if(b.action.equals("leave")) b.label.setText(game.playing()?"Stand & leave":"Leave");
            b.label.setColor(b.enabled?Color.WHITE:DIM.darker());
        }
        if(game.rounds()>reportedRounds) {
            reportedRounds=game.rounds();
            Global.getSoundPlayer().playUISound(game.paid()>game.invested()?"ui_chip_pickup":"ui_button_pressed",1,.6f);
        }
    }
    private void arrange(int group, List<Card> cards, float left, float top, float width, boolean hideHole) {
        for(Face face:faces[group]) { face.visible=false; face.rankText=""; }
        int count=Math.max(1,cards.size()), columns=count>10?(count+1)/2:count;
        float w=count>10?58:86, h=count>10?66:116;
        for(int i=0;i<count;i++) {
            int row=i/columns, column=i%columns, rowCount=Math.min(columns,count-row*columns);
            float step=rowCount<=1?0:Math.min(w+10,(width-w)/(rowCount-1));
            Face face=faces[group][i]; face.visible=true; face.card=cards.isEmpty()?null:cards.get(i);
            face.hidden=hideHole && i==1; face.x=left+(width-w-step*(rowCount-1))/2+column*step;
            face.y=top+row*(h+6); face.w=w; face.h=h;
            face.rankText=face.hidden || face.card==null?"":face.card.symbol();
        }
    }
    private void act(String action) {
        if(dismissed) return;
        if(action.equals("leave")) {
            finishOnDismissal(); if(callbacks!=null) callbacks.dismissDialog(); return;
        }
        if(action.startsWith("bet:")) {
            if(game.playing()) return;
            int next=proposedBet(action);
            if(next<2 || next>1000 || next==bet) return;
            bet=next; game.clearResult();
        } else {
            boolean acted=switch(action) {
                case "deal" -> game.deal(bet); case "hit" -> game.hit(); case "stand" -> game.stand();
                case "double" -> game.doubleDown(); case "split" -> game.split(); default -> false;
            };
            if(acted) { dealerTimer=0; Global.getSoundPlayer().playUISound("ui_button_pressed",1,.5f); }
        }
        refresh();
    }
    private void pointer(float mx, float my, boolean down) {
        boolean click=down && !wasMouseDown; wasMouseDown=down;
        for(Btn b:buttons) b.hovered=b.enabled && b.contains(mx,my);
        if(!click || dismissed) return;
        for(Btn b:buttons) if(b.contains(mx,my)) { if(b.enabled) act(b.action); return; }
    }
    @Override public void advance(float amount) {
        if(dismissed) return;
        if(position!=null && Mouse.isCreated()) {
            float scale=Global.getSettings().getScreenScaleMult();
            if(scale>0) pointer(Mouse.getX()/scale-position.getX(),position.getY()+PANEL_H-Mouse.getY()/scale,Mouse.isButtonDown(0));
        }
        if(game.state()==State.DEALER && Float.isFinite(amount) && amount>0) {
            dealerTimer+=amount;
            if(dealerTimer>=.42f) { dealerTimer=0; game.stepDealer(); refresh(); }
        }
    }
    @Override public void processInput(List<InputEventAPI> events) {
        if(events==null || dismissed) return;
        for(InputEventAPI event:events) {
            if(event.isConsumed() || !event.isKeyDownEvent()) continue;
            if(event.getEventValue()==Keyboard.KEY_ESCAPE) { event.consume(); act("leave"); return; }
            if(event.getEventValue()==Keyboard.KEY_SPACE && !game.playing()) { event.consume(); act("deal"); }
        }
    }
    public void finishOnDismissal() {
        if(dismissed) return;
        dismissed=true; game.finish(); if(panel!=null) refresh();
    }
    public String getSessionSummary() {
        return game.rounds()==0?"":"Blackjack: "+game.rounds()+" rounds, "+(game.sessionNet()>0?"+":"")+game.sessionNet()+" tokens.";
    }
    @Override public void positionChanged(PositionAPI position) { this.position=position; }
    private float x(float n) { return position.getX()+n; }
    private float y(float n) { return position.getY()+PANEL_H-n; }
    private void rect(float x,float y,float w,float h,Color c,float a) { GLDraw.quad(x(x),y(y+h),w,h,c,a); }
    private void frame(float x,float y,float w,float h,Color c,float a) { GLDraw.frame(x(x),y(y+h),w,h,c,2,a); }
    private static Color ink(Card c) { return c.suit()==Suit.HEARTS || c.suit()==Suit.DIAMONDS?RED:INK; }
    @Override public void renderBelow(float alpha) {
        if(position==null) return;
        // Only write GL state. Synchronous queries crash the user's Fast Rendering bridge.
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT|GL11.GL_COLOR_BUFFER_BIT|GL11.GL_SCISSOR_BIT|GL11.GL_CURRENT_BIT|GL11.GL_LINE_BIT|GL11.GL_TEXTURE_BIT);
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D); GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
            rect(0,0,PANEL_W,PANEL_H,BG,alpha);
            GLDraw.bevelledPanel(x(26),y(505),948,410,new Color(53,44,35),6,alpha);
            rect(36,105,928,390,FELT,alpha); frame(40,109,920,382,GOLD,alpha*.2f);
            GLDraw.line(x(76),y(299),x(924),y(299),GOLD,1,alpha*.22f);
            if(game.state()==State.PLAYER) frame(game.hands().size()==2?46+game.active()*468:46,311,
                    game.hands().size()==2?440:908,180,GOLD,alpha*.65f);
            for(Face[] group:faces) for(Face f:group) if(f!=null && f.visible) drawCard(f,alpha);
            for(Btn b:buttons) {
                Color fill=!b.enabled?new Color(22,26,33):b.checked?new Color(98,76,38):b.hovered?new Color(55,72,78):new Color(32,43,50);
                GLDraw.bevelledPanel(x(b.x),y(b.y+b.h),b.w,b.h,fill,3,alpha);
                frame(b.x,b.y,b.w,b.h,b.checked?GOLD:DIM,alpha*(b.enabled?.65f:.2f));
            }
        } finally { GL11.glPopAttrib(); }
    }
    private void drawCard(Face f,float a) {
        if(f.card==null) { frame(f.x,f.y,f.w,f.h,DIM,a*.25f); return; }
        rect(f.x+3,f.y+4,f.w,f.h,Color.BLACK,a*.35f);
        rect(f.x,f.y,f.w,f.h,new Color(242,234,215),a);
        if(f.hidden) {
            rect(f.x+4,f.y+4,f.w-8,f.h-8,new Color(47,67,94),a);
            for(float yy=f.y+12;yy<f.y+f.h-8;yy+=12) for(float xx=f.x+12;xx<f.x+f.w-8;xx+=12)
                diamond(xx,yy,2,GOLD,a*.6f);
            frame(f.x+7,f.y+7,f.w-14,f.h-14,GOLD,a*.65f);
        } else {
            CardRanks.draw(f.rankText,x(f.x+8),y(f.y+8),f.h>80?22:17,ink(f.card),a);
            suit(f.card.suit(),f.x+f.w/2,f.y+f.h*.57f,Math.min(18,f.h*.2f),ink(f.card),a);
            if(f.h>80) suit(f.card.suit(),f.x+15,f.y+43,5,ink(f.card),a);
        }
    }
    private void triangle(float ax,float ay,float bx,float by,float cx,float cy,Color c,float a) {
        GL11.glColor4f(c.getRed()/255f,c.getGreen()/255f,c.getBlue()/255f,a);
        GL11.glBegin(GL11.GL_TRIANGLES);
        GL11.glVertex2f(x(ax),y(ay)); GL11.glVertex2f(x(bx),y(by)); GL11.glVertex2f(x(cx),y(cy)); GL11.glEnd();
    }
    private void diamond(float cx,float cy,float r,Color c,float a) {
        triangle(cx-r*.75f,cy,cx,cy-r,cx+r*.75f,cy,c,a);
        triangle(cx-r*.75f,cy,cx,cy+r,cx+r*.75f,cy,c,a);
    }
    private void suit(Suit suit,float cx,float cy,float r,Color c,float a) {
        if(suit==Suit.DIAMONDS) { diamond(cx,cy,r,c,a); return; }
        if(suit==Suit.CLUBS) {
            GLDraw.circle(x(cx),y(cy-r*.55f),r*.48f,c,a,18);
            GLDraw.circle(x(cx-r*.48f),y(cy+r*.05f),r*.48f,c,a,18);
            GLDraw.circle(x(cx+r*.48f),y(cy+r*.05f),r*.48f,c,a,18);
        } else {
            float flip=suit==Suit.HEARTS?1:-1;
            GLDraw.circle(x(cx-r*.42f),y(cy-flip*r*.32f),r*.52f,c,a,18);
            GLDraw.circle(x(cx+r*.42f),y(cy-flip*r*.32f),r*.52f,c,a,18);
            triangle(cx-r*.93f,cy-flip*r*.12f,cx+r*.93f,cy-flip*r*.12f,cx,cy+flip*r,c,a);
            if(suit==Suit.HEARTS) return;
        }
        triangle(cx,cy,cx-r*.4f,cy+r,cx+r*.4f,cy+r,c,a);
    }
}
