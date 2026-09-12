package gamblingden.pinball;

import java.util.List;
import java.util.Random;

/** Fixed-step ball physics. Moving flipper contacts, not a prize roll, determine the score. */
public final class PinballBoard {
    public static final float WIDTH=460, HEIGHT=530, BALL_RADIUS=7, FLIPPER_LENGTH=96, FLIPPER_RADIUS=8;
    public static final float MAX_TABLE_SPEED=620;
    public static final double STEP=1d/240;
    public record Rail(float x1,float y1,float x2,float y2) { }
    public record Bumper(float x,float y,float radius) { }
    public static final Rail SHOOTER_GATE=new Rail(416,130,448,98);
    public static final List<Rail> RAILS=List.of(
            new Rail(16,88,84,22),new Rail(84,22,366,22),new Rail(366,22,448,88),
            new Rail(16,88,16,530),new Rail(448,88,448,530),new Rail(416,130,416,510),
            new Rail(40,330,72,415),new Rail(72,415,118,468),
            new Rail(394,330,374,415),new Rail(374,415,342,468),
            new Rail(63,282,48,126),new Rail(48,126,93,63),new Rail(93,63,154,52));
    public static final List<Bumper> BUMPERS=List.of(new Bumper(174,163,24),
            new Bumper(275,149,24),new Bumper(225,245,24));
    public static final List<Bumper> TARGETS=List.of(new Bumper(108,113,10),
            new Bumper(240,69,10),new Bumper(364,228,10));
    // Both slingshots clear the side rails by more than a ball's width. Set closer, they
    // pinched the outlanes into dead ends that caught and held the ball for good.
    public static final List<Rail> SLINGS=List.of(new Rail(102,341,162,398),new Rail(162,398,112,407),
            new Rail(112,407,102,341),new Rail(352,341,292,398),new Rail(292,398,342,407),new Rail(342,407,352,341));
    private double x=435,y=489,vx,vy,remainder,left=.35,right=.35,leftOmega,rightOmega;
    private double heat,nudgeCooldown,quiet,loopWindow,anchorX=435,anchorY=489;
    private final double launchX,launchY;
    private boolean launched,drained,tilted,loopArmed,shooterGateClosed;
    private int score,mask,multiplier=1,flipperHits,bumperHits,loops;
    private final double[] bumperCooldown=new double[3],targetCooldown=new double[3],slingCooldown=new double[2];
    private final float[] bumperFlash=new float[3],targetFlash=new float[3];
    public PinballBoard(Random random) {
        launchX=-8-random.nextDouble()*8;launchY=-680-random.nextDouble()*20;
    }
    public boolean launch() {
        if(launched || drained) return false;
        launched=true;vx=launchX;vy=launchY;return true;
    }
    public boolean nudge() {
        if(!playing() || tilted || nudgeCooldown>0) return false;
        nudgeCooldown=.45;heat+=1;
        if(heat>2.5) { tilted=true;return true; }
        vx+=x<WIDTH/2?100:-100;vy-=155;return true;
    }
    public void advance(float seconds,boolean leftDown,boolean rightDown) {
        if(!Float.isFinite(seconds) || seconds<=0 || !playing()) return;
        remainder+=Math.min(seconds,.25f);
        // Do not drop a boundary tick from floating-point subtraction: that would apply
        // a flipper change one step late at some frame rates and alter the shot.
        while(remainder+1e-9>=STEP && playing()) {
            remainder=Math.max(0,remainder-STEP);step(leftDown,rightDown);
        }
    }
    private void step(boolean leftDown,boolean rightDown) {
        heat=Math.max(0,heat-STEP*.25);nudgeCooldown=Math.max(0,nudgeCooldown-STEP);
        for(int i=0;i<3;i++) {
            bumperCooldown[i]=Math.max(0,bumperCooldown[i]-STEP);targetCooldown[i]=Math.max(0,targetCooldown[i]-STEP);
            bumperFlash[i]=Math.max(0,bumperFlash[i]-(float)STEP*3);targetFlash[i]=Math.max(0,targetFlash[i]-(float)STEP*3);
        }
        for(int i=0;i<2;i++) slingCooldown[i]=Math.max(0,slingCooldown[i]-STEP);
        double next=move(left,leftDown&&!tilted?-.55:.35,(leftDown&&!tilted?12:8)*STEP);
        leftOmega=(next-left)/STEP;left=next;
        next=move(right,rightDown&&!tilted?-.55:.35,(rightDown&&!tilted?12:8)*STEP);
        rightOmega=(next-right)/STEP;right=next;
        vy+=420*STEP;vx*=.9995;
        limitSpeed();x+=vx*STEP;y+=vy*STEP;
        for(Rail rail:RAILS) segment(rail,2,0,0,.45,0);
        // Close only after the whole ball clears the diagonal gate. A returning ball
        // then rolls left into the table instead of falling down the shooter lane.
        if(!shooterGateClosed && x+y<546-(BALL_RADIUS+2.1)*Math.sqrt(2)) shooterGateClosed=true;
        if(shooterGateClosed) segment(SHOOTER_GATE,2,0,0,.32,0);
        for(int i=0;i<SLINGS.size();i++) {
            int side=i/3;
            if(segment(SLINGS.get(i),3,0,0,.42,tilted||slingCooldown[side]>0?0:45) && !tilted && slingCooldown[side]<=0) {
                points(35);slingCooldown[side]=.2;
            }
        }
        for(int i=0;i<3;i++) {
            if(circle(BUMPERS.get(i),.52,tilted||bumperCooldown[i]>0?0:95)) {
                if(!tilted && bumperCooldown[i]<=0) { points(100);bumperHits++;bumperFlash[i]=1;bumperCooldown[i]=.16; }
            }
            if(circle(TARGETS.get(i),.4,tilted||targetCooldown[i]>0?0:20) && !tilted && targetCooldown[i]<=0) {
                points(250);mask|=1<<i;targetFlash[i]=1;targetCooldown[i]=.4;
                if(mask==7) { points(1000);mask=0;multiplier=Math.min(3,multiplier+1); }
            }
        }
        flipper(true);flipper(false);
        if(!tilted) {
            if(x<100 && y>165 && y<270 && vy< -50) { loopArmed=true;loopWindow=2; }
            if(loopArmed) {
                loopWindow-=STEP;
                if(y<100 && x<155) { points(500);loops++;loopArmed=false; }
                if(loopWindow<=0) loopArmed=false;
            }
        }
        limitSpeed();
        // A ball that stops going anywhere gets a small unscored rescue; flipper cradles are allowed.
        // Measured as travel, not speed: a ball balanced on a target hops in place forever otherwise,
        // because the target's own kick keeps its speed up while it never actually leaves.
        if(!cradled() && Math.hypot(x-anchorX,y-anchorY)<3) quiet+=STEP;
        else { quiet=0;anchorX=x;anchorY=y; }
        if(quiet>3) { vx=x<WIDTH/2?65:-65;vy=-110;quiet=0;anchorX=x;anchorY=y; }
        if(y>HEIGHT+BALL_RADIUS || !Double.isFinite(x+y+vx+vy)) drain();
    }
    private void points(int amount) { if(!tilted) score=Math.min(999999,score+amount*multiplier); }
    private static double move(double from,double to,double step) { return from<to?Math.min(to,from+step):Math.max(to,from-step); }
    private void limitSpeed() {
        double speed=Math.hypot(vx,vy);
        double limit=shooterGateClosed?MAX_TABLE_SPEED:700;
        if(speed>limit) { vx*=limit/speed;vy*=limit/speed; }
    }
    private boolean circle(Bumper bumper,double bounce,double boost) {
        double dx=x-bumper.x,dy=y-bumper.y,r=BALL_RADIUS+bumper.radius,dist=Math.hypot(dx,dy);
        if(dist>=r) return false;
        double nx=dist>1e-8?dx/dist:0,ny=dist>1e-8?dy/dist:-1;
        x=bumper.x+nx*(r+.02);y=bumper.y+ny*(r+.02);
        double approach=vx*nx+vy*ny;
        if(approach>=0) return false;
        vx-=((1+bounce)*approach-boost)*nx;vy-=((1+bounce)*approach-boost)*ny;
        return true;
    }
    private boolean segment(Rail rail,double radius,double surfaceX,double surfaceY,double bounce,double boost) {
        double dx=rail.x2-rail.x1,dy=rail.y2-rail.y1;
        double t=Math.max(0,Math.min(1,((x-rail.x1)*dx+(y-rail.y1)*dy)/(dx*dx+dy*dy)));
        double cx=rail.x1+dx*t,cy=rail.y1+dy*t,nx=x-cx,ny=y-cy,d=Math.hypot(nx,ny),r=BALL_RADIUS+radius;
        if(d>=r) return false;
        if(d>1e-8) { nx/=d;ny/=d; }else { nx=0;ny=-1; }
        x=cx+nx*(r+.02);y=cy+ny*(r+.02);
        double approach=(vx-surfaceX)*nx+(vy-surfaceY)*ny;
        if(approach>=0) return false;
        vx-=((1+bounce)*approach-boost)*nx;vy-=((1+bounce)*approach-boost)*ny;
        return true;
    }
    /** A ball settled on a flipper is a cradle the player owns, so it never gets the wedge rescue. */
    private boolean cradled() { return onFlipper(flipperRail(true)) || onFlipper(flipperRail(false)); }
    private boolean onFlipper(Rail rail) {
        double dx=rail.x2-rail.x1,dy=rail.y2-rail.y1;
        double t=Math.max(0,Math.min(1,((x-rail.x1)*dx+(y-rail.y1)*dy)/(dx*dx+dy*dy)));
        // Only the blade cradles a ball. A contact back at the pivot is the pocket between a held
        // flipper and the side wall, which the player cannot shake loose and so must be rescued.
        return t>BALL_RADIUS*2/FLIPPER_LENGTH
                && Math.hypot(x-(rail.x1+dx*t),y-(rail.y1+dy*t))<BALL_RADIUS+FLIPPER_RADIUS+6;
    }
    private void flipper(boolean isLeft) {
        Rail rail=flipperRail(isLeft);
        double dx=rail.x2-rail.x1,dy=rail.y2-rail.y1;
        double t=Math.max(0,Math.min(1,((x-rail.x1)*dx+(y-rail.y1)*dy)/(dx*dx+dy*dy)));
        double omega=isLeft?leftOmega:-rightOmega;
        if(segment(rail,FLIPPER_RADIUS,-dy*t*omega,dx*t*omega,.42,0) && Math.abs(omega)>1) flipperHits++;
    }
    public Rail flipperRail(boolean isLeft) {
        double angle=isLeft?left:right;
        float px=isLeft?118:342;
        return new Rail(px,468,px+(isLeft?1:-1)*(float)Math.cos(angle)*FLIPPER_LENGTH,
                468+(float)Math.sin(angle)*FLIPPER_LENGTH);
    }
    public void drain() { drained=true;vx=vy=0; }
    public boolean playing() { return launched&&!drained; }
    public boolean ready() { return !launched&&!drained; }
    public boolean drained() { return drained; }
    public boolean tilted() { return tilted; }
    public boolean shooterGateClosed() { return shooterGateClosed; }
    public float x() { return (float)x; } public float y() { return (float)y; }
    public float vx() { return (float)vx; } public float vy() { return (float)vy; }
    public int score() { return score; } public int multiplier() { return multiplier; }
    public int targetMask() { return mask; } public int flipperHits() { return flipperHits; }
    public int bumperHits() { return bumperHits; } public int loops() { return loops; }
    public float heat() { return (float)heat; } public boolean canNudge() { return playing()&&!tilted&&nudgeCooldown<=0; }
    public float bumperFlash(int i) { return bumperFlash[i]; } public float targetFlash(int i) { return targetFlash[i]; }
}
