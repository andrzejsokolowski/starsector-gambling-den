package gamblingden.tests;

import java.util.Random;
import gamblingden.pinball.PinballBoard;

public final class PinballPhysicsChecks {
    private static void set(PinballBoard board,String name,Object value) throws Exception {
        var field=PinballBoard.class.getDeclaredField(name);field.setAccessible(true);field.set(board,value);
    }
    private static double gap(PinballBoard.Rail rail,double px,double py) {
        double dx=rail.x2()-rail.x1(),dy=rail.y2()-rail.y1();
        double t=Math.max(0,Math.min(1,((px-rail.x1())*dx+(py-rail.y1())*dy)/(dx*dx+dy*dy)));
        return Math.hypot(px-(rail.x1()+dx*t),py-(rail.y1()+dy*t));
    }
    /** Whether a ball centred here overlaps no fixed obstacle, so it is a place a ball can actually be. */
    private static boolean fits(double px,double py) {
        for(var rail:PinballBoard.RAILS) if(gap(rail,px,py)<PinballBoard.BALL_RADIUS+2) return false;
        for(var sling:PinballBoard.SLINGS) if(gap(sling,px,py)<PinballBoard.BALL_RADIUS+3) return false;
        for(var bumper:PinballBoard.BUMPERS)
            if(Math.hypot(px-bumper.x(),py-bumper.y())<PinballBoard.BALL_RADIUS+bumper.radius()) return false;
        return true;
    }
    /** Neither outlane may pinch below a ball's width, and no square may hold a ball forever. */
    private static void wedges() throws Exception {
        double clearance=2*PinballBoard.BALL_RADIUS+2+3+6;
        for(int side=0;side<2;side++) {
            double narrowest=Double.MAX_VALUE,atX=0,atY=0;
            for(var wall:PinballBoard.RAILS.subList(6+side*2,8+side*2)) for(int i=0;i<=200;i++) {
                double px=wall.x1()+(wall.x2()-wall.x1())*i/200d,py=wall.y1()+(wall.y2()-wall.y1())*i/200d;
                for(var sling:PinballBoard.SLINGS.subList(side*3,side*3+3)) {
                    double d=gap(sling,px,py);
                    if(d<narrowest) { narrowest=d;atX=px;atY=py; }
                }
            }
            if(narrowest<clearance) throw new AssertionError((side==0?"Left":"Right")+" outlane pinches to "
                    +Math.round(narrowest)+" units at "+Math.round(atX)+","+Math.round(atY)+"; a ball needs "+Math.round(clearance));
        }
        int squares=0,frames=240*15;
        for(int mode=0;mode<2;mode++) for(int gx=24;gx<=440;gx+=8) for(int gy=32;gy<=496;gy+=8) {
            if(!fits(gx,gy)) continue;
            if(mode==0) squares++;
            var drop=new PinballBoard(new Random(1));drop.launch();
            set(drop,"shooterGateClosed",true);set(drop,"x",(double)gx);set(drop,"y",(double)gy);
            set(drop,"vx",0d);set(drop,"vy",0d);
            float lastX=drop.x(),lastY=drop.y();int moved=0;
            for(int frame=0;frame<frames&&drop.playing();frame++) {
                drop.advance(1f/240,mode==1,mode==1);
                if(Math.hypot(drop.x()-lastX,drop.y()-lastY)>1) { moved=frame;lastX=drop.x();lastY=drop.y(); }
            }
            if(drop.playing()&&moved<frames-240*6) throw new AssertionError("Ball dropped at "+gx+","+gy
                    +(mode==1?" with both flippers held":"")+" wedged at "+drop.x()+","+drop.y());
        }
        System.out.println("Pinball wedges: both outlanes clear a ball, and "+squares+" drop squares all freed the ball.");
    }
    public static void run() throws Exception {
        for(double x:new double[]{426,432,438}) for(double vx:new double[]{-200,0,200}) for(double vy:new double[]{100,400,1200}) {
            var gate=new PinballBoard(new Random(1));gate.launch();
            set(gate,"shooterGateClosed",true);set(gate,"x",x);set(gate,"y",94d);set(gate,"vx",vx);set(gate,"vy",vy);
            boolean reachedTable=false;
            for(int step=0;step<480&&gate.playing();step++) {
                gate.advance(1f/240,false,false);
                if(gate.x()>423&&gate.y()>150&&gate.y()<500) throw new AssertionError("Return gate leaked into shooter lane: start="
                        +x+","+vx+","+vy+" step="+step+" at="+gate.x()+","+gate.y());
                if(gate.x()<400) reachedTable=true;
            }
            if(!reachedTable) throw new AssertionError("Return gate trapped the ball");
        }
        var bounce=new PinballBoard(new Random(1));bounce.launch();set(bounce,"shooterGateClosed",true);
        set(bounce,"x",174d);set(bounce,"y",132d);set(bounce,"vx",0d);set(bounce,"vy",350d);
        bounce.advance(1f/240,false,false);
        double firstRebound=-bounce.vy();int firstScore=bounce.score();
        if(firstRebound<=0||firstRebound>=350||firstScore!=100) throw new AssertionError("Bumper still accelerates an ordinary impact excessively");
        set(bounce,"x",174d);set(bounce,"y",132d);set(bounce,"vx",0d);set(bounce,"vy",350d);
        bounce.advance(1f/240,false,false);
        if(bounce.score()!=firstScore||-bounce.vy()>firstRebound-90) throw new AssertionError("Bumper kick bypassed its cooldown");
        double passive=0,held=0;
        for(String mode:new String[]{"passive","held","timed"}) {
            long points=0,hits=0,bumpers=0;int timeouts=0,loops=0;
            for(int seed=0;seed<150;seed++) {
                var board=new PinballBoard(new Random(seed*7919L));
                if(!board.launch() || board.launch()) throw new AssertionError("Launch state");
                for(int step=0;step<240*90 && board.playing();step++) {
                    boolean flip=mode.equals("held") || mode.equals("timed")&&board.y()>435 && board.vy()>0;
                    board.advance(1f/240,flip&&board.x()<270,flip&&board.x()>190);
                    if(!Float.isFinite(board.x()+board.y()+board.vx()+board.vy())
                            || board.x()<0||board.x()>PinballBoard.WIDTH||board.y()<0) throw new AssertionError("Invalid physics");
                }
                if(board.playing()) timeouts++;
                points+=board.score();hits+=board.flipperHits();bumpers+=board.bumperHits();loops+=board.loops();
            }
            System.out.printf(java.util.Locale.ROOT,"Pinball %s: %.1f score/ball, %d flipper hits, %d bumper hits, %d loops, %d/150 still active after 90s%n",
                    mode,points/150d,hits,bumpers,loops,timeouts);
            if(mode.equals("passive")) passive=points/150d;
            if(mode.equals("held")) held=points/150d;
            if(mode.equals("timed")&&(points/150d<passive*2||points/150d<held*2||hits==0||loops==0))
                throw new AssertionError("Flipper timing does not materially improve play");
        }
        for(int seed=0;seed<40;seed++) {
            PinballBoard baseline=null;
            for(float dt:new float[]{1f/240,1f/60,.125f,.25f}) {
                var board=new PinballBoard(new Random(seed));board.launch();
                for(int block=0;block<16;block++) for(int frame=0;frame<Math.round(1/dt);frame++)
                    board.advance(dt,block%3==0,block%4==1);
                if(baseline==null) baseline=board;
                else if(board.score()!=baseline.score()||board.drained()!=baseline.drained()
                        ||Math.abs(board.x()-baseline.x())>.05f||Math.abs(board.y()-baseline.y())>.05f)
                    throw new AssertionError("Frame rate changed fixed-input pinball: seed="+seed+" dt="+dt
                            +" score="+board.score()+"/"+baseline.score()+" xy="+board.x()+","+board.y()+"/"+baseline.x()+","+baseline.y());
            }
        }
        var tilt=new PinballBoard(new Random(8));tilt.launch();
        for(int i=0;i<3;i++) {
            set(tilt,"x",230d);set(tilt,"y",310d);set(tilt,"vx",0d);set(tilt,"vy",0d);
            if(!tilt.nudge()) throw new AssertionError("Valid nudge rejected");
            if(tilt.nudge()) throw new AssertionError("Nudge cooldown missing");
            if(i<2) { tilt.advance(.25f,false,false);tilt.advance(.25f,false,false); }
        }
        if(!tilt.tilted()||tilt.canNudge()) throw new AssertionError("Excessive nudges did not tilt");
        int score=tilt.score();set(tilt,"x",174d);set(tilt,"y",123d);set(tilt,"vx",0d);set(tilt,"vy",250d);
        tilt.advance(.25f,true,true);
        if(tilt.score()!=score||tilt.flipperRail(true).y2()<468) throw new AssertionError("Tilt still scores or powers flippers");
        float x=tilt.x(),y=tilt.y();
        tilt.advance(Float.NaN,true,true);tilt.advance(Float.POSITIVE_INFINITY,true,true);tilt.advance(-10,true,true);
        if(tilt.x()!=x||tilt.y()!=y) throw new AssertionError("Invalid time altered ball");
        tilt.drain();tilt.advance(20,true,true);
        if(!tilt.drained()||tilt.launch()||tilt.nudge()) throw new AssertionError("Drained ball restarted");
        wedges();
        System.out.println("PASS: pinball return gate, softened impacts, skill/control comparison, bounds, frame-rate consistency, tilt, cooldowns, invalid time, and no wedge anywhere on the table.");
    }
    public static void main(String[] args) throws Exception { run(); }
}
