package gamblingden.pinball;

import java.awt.Color;
import java.util.Arrays;
import gamblingden.Ids;
import gamblingden.economy.BlueprintPool;
import gamblingden.economy.TokenBank;
import gamblingden.prizes.*;
import lunalib.lunaSettings.LunaSettings;

public final class PinballSettings {
    public static final int BALLS=3;
    public enum Category {
        HULLMODS("Hullmods","hullmods",Prize.BOX_SMALL,new int[]{1,2,4,8},new Color(145,190,245)),
        WEAPONS("Weapons","weapons",Prize.WEAPONS_SMALL,new int[]{2,5,12,25},new Color(235,170,105)),
        FIGHTERS("Fighters","fighters",Prize.FIGHTERS_SMALL,new int[]{1,2,4,8},new Color(130,220,225)),
        CREDITS("Credits","credits",Prize.CREDITS,new int[]{10000,30000,75000,200000},new Color(240,205,105)),
        TOKENS("Tokens","tokens",Prize.TOKENS,new int[]{5,15,35,80},new Color(140,225,165));
        public final String label,id;
        public final Prize prize;
        public final Color color;
        private final int[] defaults;
        Category(String label,String id,Prize prize,int[] defaults,Color color) {
            this.label=label;this.id=id;this.prize=prize;this.defaults=defaults;this.color=color;
        }
        public String amountText(int amount) {
            String unit=switch(this) { case HULLMODS -> amount==1?"blueprint":"blueprints";
                case WEAPONS -> amount==1?"weapon":"weapons";case FIGHTERS -> amount==1?"fighter LPC":"fighter LPCs";
                case CREDITS -> "credits";case TOKENS -> amount==1?"token":"tokens"; };
            return group(amount)+" "+unit;
        }
    }
    private static final int[] SCORES={2000,6000,12000,24000};
    private static int setting(String key,int fallback,int min,int max) {
        Integer value=null;
        try { value=LunaSettings.getInt(Ids.MOD_ID,key); }catch(Exception ignored) { }
        return Math.max(min,Math.min(max,value==null?fallback:value));
    }
    public static Offer quote(Category category) {
        int cost=setting("gd_pinball_price",10,1,1000);
        int available=switch(category) {
            case HULLMODS -> BlueprintPool.getEligible().size();
            case WEAPONS -> WeaponPool.isEmpty()?0:100;
            case FIGHTERS -> FighterPool.isEmpty()?0:100;
            case TOKENS -> (int)Math.min(1000000,(long)Integer.MAX_VALUE-TokenBank.getTokens()+cost);
            case CREDITS -> 2000000;
        };
        int[] scores=new int[4],amounts=new int[4];
        for(int i=0;i<4;i++) {
            scores[i]=setting("gd_pinball_score_"+i,SCORES[i],i==0?1:scores[i-1]+1,1000000+i);
            int max=category==Category.CREDITS?2000000:category==Category.TOKENS?1000000:100;
            amounts[i]=Math.min(available,setting("gd_pinball_"+category.id+"_"+i,category.defaults[i],0,max));
            if(i>0) amounts[i]=Math.max(amounts[i-1],amounts[i]);
        }
        return new Offer(category,cost,scores,amounts);
    }
    public static final class Offer {
        public final Category category;public final int cost;
        private final int[] scores,amounts;
        private Offer(Category category,int cost,int[] scores,int[] amounts) {
            this.category=category;this.cost=cost;this.scores=scores.clone();this.amounts=amounts.clone();
        }
        public int scoreAt(int tier) { return scores[tier]; }
        public int amountAt(int tier) { return amounts[tier]; }
        public int tier(int score) { int tier=-1;for(int i=0;i<4;i++) if(score>=scores[i]) tier=i;return tier; }
        public int awardAt(int score) { int tier=tier(score);return tier<0?0:amounts[tier]; }
        public boolean available() { return amounts[3]>0; }
        public boolean same(Offer other) {
            return other!=null && category==other.category && cost==other.cost
                    && Arrays.equals(scores,other.scores) && Arrays.equals(amounts,other.amounts);
        }
    }
    public static String group(int n) { return String.format(java.util.Locale.ROOT,"%,d",n); }
    private PinballSettings() { }
}
