package gamblingden.tests;

import java.lang.reflect.Field;
import java.util.*;
import gamblingden.blackjack.BlackjackGame;
import gamblingden.blackjack.BlackjackGame.*;

final class BlackjackChecks {
    private static int checks;
    private static void check(boolean ok,String message) { checks++; if(!ok) throw new AssertionError(message); }
    static final class Bank implements Account {
        int balance, payments; boolean reject;
        Bank(int balance) { this.balance=balance; }
        public int balance() { return balance; }
        public boolean take(int n) { if(reject || n<=0 || n>balance) return false; balance-=n; return true; }
        public int pay(int n) { check(n>=0 && (long)balance+n<=Integer.MAX_VALUE,"Blackjack overflow/negative payment"); payments++; balance+=n; return n; }
    }
    @SuppressWarnings("unchecked")
    static void rig(BlackjackGame game,int... ranks) throws Exception {
        Field f=BlackjackGame.class.getDeclaredField("shoe"); f.setAccessible(true);
        List<Card> shoe=(List<Card>)f.get(game); shoe.clear();
        for(int i=0;i<80;i++) shoe.add(new Card(10,Suit.values()[i%4]));
        for(int i=ranks.length-1;i>=0;i--) shoe.add(new Card(ranks[i],Suit.values()[i%4]));
    }
    private static BlackjackGame game(Bank bank,int... ranks) throws Exception {
        BlackjackGame g=new BlackjackGame(bank,new Random(1)); rig(g,ranks); return g;
    }
    private static int value(List<Card> cards) {
        int sum=0; boolean ace=false;
        for(Card c:cards) { sum+=c.rank()==1?1:Math.min(c.rank(),10); ace|=c.rank()==1; }
        return ace && sum<=11?sum+10:sum;
    }
    private static int oracle(BlackjackGame game) {
        int d=value(game.dealer().cards()), result=0;
        boolean dn=game.dealer().cards().size()==2 && d==21;
        for(Hand h:game.hands()) {
            int p=value(h.cards()); boolean pn=game.hands().size()==1 && h.cards().size()==2 && p==21;
            if(p>21) continue;
            if(dn) { if(pn) result+=h.bet(); }
            else if(pn) result+=h.bet()*5/2;
            else if(d>21 || p>d) result+=2*h.bet();
            else if(p==d) result+=h.bet();
        }
        return result;
    }
    static void run() throws Exception {
        Bank b=new Bank(100); BlackjackGame g=game(b,1,9,13,7);
        check(g.deal(10) && b.balance==115 && g.paid()==25 && g.state()==State.RESULT,"Natural does not pay 3:2");
        check(g.result().endsWith("Payout: 25 tokens"),"Natural result must show the payment including the stake");
        check(g.dealer().cards().size()==2,"Dealer drew against a natural");
        g.finish(); g.finish(); g.hit(); g.stand(); g.split(); g.doubleDown();
        check(b.balance==115 && b.payments==1 && g.rounds()==1,"Finished round paid twice");
        b=new Bank(100); g=game(b,1,13,10,1); g.deal(10);
        check(b.balance==100 && g.hands().get(0).outcome().equals("Push"),"Both naturals did not push");
        check(g.result().equals("Push  |  Payout: 10 tokens"),"Push result payment unclear");
        b=new Bank(100); g=game(b,9,1,2,13); g.deal(10);
        check(g.state()==State.RESULT && !g.hit() && !g.doubleDown() && b.balance==90,"Dealer natural peek failed");
        check(g.result().equals("Dealer blackjack  |  Payout: 0 tokens"),"Loss result implies another deduction");
        b=new Bank(100); g=game(b,10,10,6,8); g.deal(10); g.finish();
        check(g.result().equals("Loss  |  Payout: 0 tokens") && b.balance==90,"Ordinary loss text/accounting wrong");
        b=new Bank(100); g=game(b,10,1,7,6); g.deal(10); g.finish();
        check(g.dealer().cards().size()==2 && b.balance==100,"Dealer hit soft 17");
        b=new Bank(100); g=game(b,1,10,1,7,9); g.deal(10);
        check(g.hands().get(0).value()==12 && g.hands().get(0).soft(),"Multiple aces misvalued");
        g.hit(); g.finish();
        check(g.hands().get(0).value()==21 && !g.hands().get(0).natural() && b.balance==110,"Drawn 21 got natural bonus");
        check(g.result().equals("Win  |  Payout: 20 tokens"),"Win result does not show actual payment");
        b=new Bank(100); g=game(b,10,6,9,10,5); g.deal(10); g.hit(); g.finish();
        check(b.balance==90 && g.hands().get(0).bust(),"Busted player won");
        b=new Bank(100); g=game(b,5,6,6,10,10,10); g.deal(10);
        check(g.doubleDown() && g.invested()==20 && g.hands().get(0).cards().size()==3,"Double did not take one extra bet/card");
        check(!g.doubleDown() && !g.hit(),"Doubled hand accepts more actions");
        g.finish(); check(b.balance==120 && g.paid()==40,"Double payout wrong");
        b=new Bank(100); g=game(b,8,10,8,6,10,6,10,5); g.deal(10); g.split(); g.stand();
        check(g.active()==1 && !g.holeRevealed(),"Split turn/hole card wrong");
        g.hit(); g.finish();
        check(g.dealer().value()==21 && b.balance==80,"Last split bust skipped dealer for surviving first hand");
        b=new Bank(100); g=game(b,8,6,8,10,10,10,10); g.deal(10); g.split();
        check(!g.canSplit(),"Re-splitting allowed"); g.finish();
        check(b.balance==120 && g.paid()==40,"Two split wins paid incorrectly");
        check(g.result().equals("Round complete  |  Payout: 40 tokens"),"Split result does not sum payments");
        b=new Bank(100); g=game(b,1,9,1,7,10,9,3); g.deal(10); g.split();
        check(g.state()==State.DEALER && !g.hit() && !g.canDouble(),"Split aces did not auto-stand"); g.finish();
        check(b.balance==120 && !g.hands().get(0).natural(),"Split ace 21 got natural bonus");
        b=new Bank(100); g=game(b,10,9,10,9,1,1); g.deal(10); g.split(); g.finish();
        check(b.balance==120,"Automatic split 21 completion wrong");
        b=new Bank(100); g=game(b,8,6,8,10,3,2,10,10,4); g.deal(10); g.split();
        check(g.doubleDown() && g.doubleDown(),"Double after split unavailable"); g.finish();
        check(g.invested()==40 && g.paid()==60 && b.balance==120,"Mixed doubled split win/push wrong");
        b=new Bank(10); g=game(b,8,9,8,8); g.deal(10);
        check(!g.canSplit() && !g.canDouble() && !g.split() && !g.doubleDown() && b.balance==0,"Unfunded split/double charged");
        g.clearResult(); check(g.state()==State.PLAYER,"Clear discarded a paid hand"); g.finish();
        b=new Bank(100); g=game(b,11,9,12,8); g.deal(10);
        check(!g.canSplit(),"Different ten-value ranks split");
        for(int n:new int[]{Integer.MIN_VALUE,-2,0,1,3,1002,Integer.MAX_VALUE}) {
            b=new Bank(10000); g=game(b,8,9,8,8);
            check(!g.deal(n) && b.balance==10000 && g.hands().isEmpty(),"Invalid bet accepted");
        }
        b=new Bank(Integer.MAX_VALUE); g=game(b,1,9,13,8);
        check(!g.deal(2) && b.balance==Integer.MAX_VALUE,"Natural token limit not guarded");
        b=new Bank(Integer.MAX_VALUE-3); g=game(b,1,9,13,8); g.deal(2);
        check(b.balance==Integer.MAX_VALUE,"Exact natural cap payout wrong");
        b=new Bank(Integer.MAX_VALUE-3); g=game(b,8,9,8,8); g.deal(2);
        check(!g.canSplit() && !g.canDouble(),"Extra stake can exceed token limit"); g.finish();
        b=new Bank(100); b.reject=true; g=game(b,8,9,8,8);
        check(!g.deal(10) && g.hands().isEmpty(),"Rejected account charge still dealt");
        b.reject=false; g.deal(10); b.reject=true;
        check(!g.split() && !g.doubleDown() && g.hands().size()==1 && g.invested()==10,"Failed extra charge mutated hand");

        Random choices=new Random(987); b=new Bank(1000000); g=new BlackjackGame(b,new Random(432));
        for(int round=0;round<20000;round++) {
            int before=b.balance;
            check(g.deal(2+2*choices.nextInt(20)),"Valid simulated bet rejected");
            int actions=0;
            while(g.state()==State.PLAYER) {
                check(++actions<50,"Player turn did not terminate");
                Hand h=g.hands().get(g.active());
                if(g.canSplit() && choices.nextBoolean()) g.split();
                else if(g.canDouble() && choices.nextInt(5)==0) g.doubleDown();
                else if(h.value()<16 && choices.nextInt(5)!=0) g.hit();
                else g.stand();
            }
            g.finish(); int paid=oracle(g);
            check(g.paid()==paid && b.balance==before-g.invested()+paid,"Independent blackjack oracle/accounting mismatch");
            check(g.result().endsWith("Payout: "+paid+" tokens") && !g.result().contains("Returned")
                    && !g.result().contains("-"),"Result displays a deduction instead of the payout");
            check(g.rounds()==round+1 && g.sessionNet()==b.balance-1000000,"Session totals wrong");
            for(Hand h:g.hands()) check(h.value()==value(h.cards()),"Hand value mismatch");
            int balance=b.balance; g.finish();
            check(b.balance==balance,"Repeated finish paid again");
        }
        System.out.println("PASS: blackjack, "+checks+" checks including 20,000 six-deck rounds and deterministic rule/accounting cases.");
    }
}
