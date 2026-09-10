package gamblingden.tests;

import java.util.Locale;
import java.util.Random;
import gamblingden.pinball.PinballBoard;

/** Launches without nudges or flippers, through the actual fixed-step simulation. */
public final class PinballLaunchChecks {
    public static void run() {
        int failures=0,returns=0,zeroPointDrains=0;
        double longestEntry=0,peak=0;
        for(int seed=0;seed<1000;seed++) {
            var board=new PinballBoard(new Random(seed*7919L));board.launch();
            boolean entered=false,returned=false;
            for(int frame=0;frame<240*8&&board.playing();frame++) {
                board.advance(1f/240,false,false);
                if(!entered&&board.x()<400&&board.y()<330) {
                    entered=true;longestEntry=Math.max(longestEntry,(frame+1)/240d);
                }
                if(entered) {
                    peak=Math.max(peak,Math.hypot(board.vx(),board.vy()));
                    if(board.x()>423&&board.y()>150&&board.y()<500) returned=true;
                }
            }
            if(!entered) failures++;
            if(returned) returns++;
            if(board.drained()&&board.score()==0) zeroPointDrains++;
        }
        System.out.printf(Locale.ROOT,"Pinball launch: %d/1000 missed the table, %d returned down the shooter lane, %d zero-point drains; longest entry %.3fs, peak table speed %.1f%n",
                failures,returns,zeroPointDrains,longestEntry,peak);
        if(failures!=0||returns!=0||longestEntry>2.5) throw new AssertionError("Launch requires rescue or permits a shooter-lane drain");
        if(peak>625) throw new AssertionError("Ball still exceeds the reduced table speed limit");
    }
    public static void main(String[] args) { run(); }
}
