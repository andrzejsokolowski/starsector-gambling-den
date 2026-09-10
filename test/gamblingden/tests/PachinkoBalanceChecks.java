package gamblingden.tests;

import java.util.Random;
import gamblingden.pachinko.*;

/** Compare payout layouts using the actual shared ball simulation, never binomial guesses. */
public final class PachinkoBalanceChecks {
    private static PachinkoBoard ball(Random random) {
        var ball=new PachinkoBoard(new Random(random.nextLong()));
        random.nextLong(); // Match paid-ball seed consumption.
        return ball;
    }
    private static void report(String mode,int[] histogram) {
        int[][] layouts={{0,1,1,1,3,12},{0,0,0,0,8,60}};
        int count=java.util.Arrays.stream(histogram).sum();
        double[] means=new double[2],variance=new double[2];int wins=0;
        for(int l=0;l<layouts.length;l++) {
            long total=0,squares=0;
            for(int i=0;i<11;i++) {
                int amount=layouts[l][Math.abs(i-5)];
                total+=(long)histogram[i]*amount;squares+=(long)histogram[i]*amount*amount;
                if(l==1&&amount>0) wins+=histogram[i];
            }
            means[l]=total/(double)count;
            variance[l]=squares/(double)count-means[l]*means[l];
        }
        System.out.printf(java.util.Locale.ROOT,"Risky tokens, %s, %d balls: %.4f tokens/ball, %.2f%% blank, %.1fx previous variance%n",
                mode,count,means[1],100d*(count-wins)/count,variance[1]/variance[0]);
        if(means[1]<.55||means[1]>=1||wins/(double)count>.06||variance[1]<variance[0]*8)
            throw new AssertionError("Risky board does not meet its return/volatility targets");
    }
    public static void run() {
        for(int size:new int[]{1,10,50,100}) {
            int[] histogram=new int[11];
            Random random=new Random(79071+size);
            for(int run=0;run<30000/size;run++) {
                var swarm=new PachinkoSwarm();
                for(int i=0;i<size;i++) swarm.add(ball(random));
                swarm.finish(null);
                for(var ball:swarm.balls()) {
                    if(ball.isJammed()) throw new AssertionError("Balance sample jammed");
                    histogram[ball.getPocket()]++;
                }
            }
            report("batch "+size,histogram);
        }
        var swarm=new PachinkoSwarm();Random random=new Random(29171);
        int created=0,finished=0;int[] histogram=new int[11];
        while(finished<30000) {
            while(created<30000 && swarm.pending()<100) { swarm.add(ball(random));created++; }
            swarm.advance(1f/60,null);
            for(var ball:swarm.balls()) if(ball.isFinished()) {
                if(ball.isJammed()) throw new AssertionError("Continuous balance sample jammed");
                histogram[ball.getPocket()]++;finished++;
            }
            swarm.retireFinished();
        }
        report("continuous 100-ball feed",histogram);
    }
    public static void main(String[] args) { run(); }
}
