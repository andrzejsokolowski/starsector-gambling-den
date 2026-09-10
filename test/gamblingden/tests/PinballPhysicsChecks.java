package gamblingden.tests;

import java.util.Random;
import gamblingden.pinball.PinballBoard;

public final class PinballPhysicsChecks {
    private static void set(PinballBoard board,String name,Object value) throws Exception {
        var field=PinballBoard.class.getDeclaredField(name);field.setAccessible(true);field.set(board,value);
    }
    public static void run() throws Exception {
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
        System.out.println("PASS: pinball skill/control comparison, bounds, frame-rate consistency, tilt, cooldowns, and invalid time.");
    }
    public static void main(String[] args) throws Exception { run(); }
}
