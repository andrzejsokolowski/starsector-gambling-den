package gamblingden.jackpot;

import java.util.*;
import org.json.*;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import gamblingden.Ids;
import gamblingden.economy.TokenBank;

/** An explicit match rate per reward tier. The visible result still decides the award. */
public final class JackpotGame {
    public static final String CONFIG_PATH="data/config/jackpot_rewards.json";
    public record Reward(String kind, String id, String data, String name, String icon, int tier, float weight) {
        public String key() { return kind+":"+id+":"+data; }
    }
    private static List<Reward> cached;
    private static float lossSymbolChance=.85f;
    private static final double[] DEFAULT_MATCH_CHANCE={.15,.20,.25};
    private static double[] matchChances=DEFAULT_MATCH_CHANCE.clone();
    private JackpotGame() { }
    public static void clearCache() { cached=null; }
    public static int clampStake(int stake) { return stake>=8?8:stake>=4?4:2; }
    public static int costOf(int stake) { return 3*clampStake(stake); }

    public static List<Reward> pool(int stake) {
        if(cached==null) load();
        int selected=clampStake(stake);
        return cached.stream().filter(r->r.tier()==selected).toList();
    }
    private static void load() {
        cached=new ArrayList<>();
        try {
            JSONObject json=Global.getSettings().getMergedJSONForMod(CONFIG_PATH,Ids.MOD_ID);
            load(json);
        } catch(Exception e) {
            Global.getLogger(JackpotGame.class).warn("Gambling Den: special-item machine unavailable; cannot read "+CONFIG_PATH,e);
        }
    }
    // Also used by the isolated tests with mocked game definitions.
    private static void load(JSONObject json) throws JSONException {
        List<Reward> loaded=new ArrayList<>();
        Set<String> seen=new HashSet<>();
        double chance=json.optDouble("lossSymbolChance",json.optDouble("symbolChance",.85));
        lossSymbolChance=(float)(Double.isFinite(chance)?Math.max(0,Math.min(1,chance)):.85);
        matchChances=DEFAULT_MATCH_CHANCE.clone();
        JSONObject rates=json.optJSONObject("matchChance");
        if(rates!=null) for(int i=0;i<3;i++) {
            double rate=rates.optDouble(Integer.toString(new int[]{2,4,8}[i]),matchChances[i]);
            if(Double.isFinite(rate)) matchChances[i]=Math.max(0,Math.min(1,rate));
        }
        JSONArray entries=json.getJSONArray("rewards");
        for(int i=0;i<entries.length();i++) {
            try {
                JSONObject row=entries.getJSONObject(i);
                String kind=row.optString("kind","special"), id=row.getString("id"), data=row.optString("data","");
                double w=row.optDouble("weight",1);
                if(!Double.isFinite(w) || w<=0 || id.isBlank()) continue;
                // Older lists retain their assigned tier, no longer a minimum unlock level.
                int stake=clampStake(row.optInt("tier",row.optInt("minStake",2)));
                String name, icon;
                if(kind.equals("special")) {
                    var spec=Global.getSettings().getSpecialItemSpec(id);
                    if(spec==null || spec.hasTag("mission_item") || spec.hasTag("no_drop") || spec.hasTag("restricted")) continue;
                    name=spec.getName(); icon=spec.getIconName();
                } else if(kind.equals("commodity")) {
                    // Only AI cores are loose commodities in this machine, never trade goods.
                    int gate=switch(id) { case "gamma_core"->2; case "beta_core"->4; case "alpha_core"->8; default->0; };
                    if(gate==0) continue;
                    stake=gate;
                    var spec=Global.getSettings().getCommoditySpec(id);
                    if(spec==null) continue;
                    name=spec.getName(); icon=spec.getIconName();
                } else continue;
                if(name==null || name.isBlank()) continue;
                Reward reward=new Reward(kind,id,data,name,icon,stake,(float)Math.min(10000,w));
                if(seen.add(reward.key())) loaded.add(reward);
            } catch(Exception e) {
                Global.getLogger(JackpotGame.class).warn("Gambling Den: skipped invalid jackpot reward row "+i,e);
            }
        }
        cached=List.copyOf(loaded);
    }
    public static double matchChance(int stake) {
        int tier=clampStake(stake);
        if(pool(tier).isEmpty()) return 0;
        Integer percent=null;
        try { percent=lunalib.lunaSettings.LunaSettings.getInt(Ids.MOD_ID,"gd_jackpot_match_"+tier); }
        catch(Exception ignored) { }
        return percent==null?matchChances[tier==2?0:tier==4?1:2]:Math.max(0,Math.min(100,percent))/100d;
    }
    public static Round buy(int stake, Random random) {
        int cost=costOf(stake);
        if(TokenBank.getTokens()<cost) return null;
        List<Reward> rewards=pool(stake);
        if(rewards.isEmpty()) return null;
        WeightedRandomPicker<Reward> picker=new WeightedRandomPicker<>(random);
        for(Reward r:rewards) picker.add(r,r.weight());
        List<Reward> symbols=new ArrayList<>();
        if(random.nextDouble()<matchChance(stake)) {
            Reward prize=picker.pick();
            for(int i=0;i<3;i++) symbols.add(prize);
        } else {
            for(int i=0;i<3;i++) symbols.add(random.nextFloat()<lossSymbolChance?picker.pick():null);
            // A losing roll must not accidentally display a paid match, even with one item.
            Reward first=symbols.get(0);
            if(first!=null && symbols.stream().allMatch(r->r!=null && r.key().equals(first.key())))
                symbols.set(random.nextInt(3),null);
        }
        // All validation and rolling precede payment; failed preparation never takes tokens.
        if(!TokenBank.spendTokens(cost)) return null;
        return new Round(symbols);
    }
    public static final class Round {
        public final List<Reward> symbols;
        private boolean finished;
        private String result="";
        private Round(List<Reward> symbols) { this.symbols=Collections.unmodifiableList(new ArrayList<>(symbols)); }
        public Reward winner() {
            Reward first=symbols.get(0);
            return first!=null && symbols.stream().allMatch(r->r!=null && r.key().equals(first.key()))?first:null;
        }
        public String finish() {
            if(finished) return result;
            var cargo=Global.getSector().getPlayerFleet().getCargo();
            if(cargo==null) throw new IllegalStateException("Player cargo is unavailable");
            finished=true;
            Reward reward=winner();
            result=reward==null?"No match.":"Collected: 1 "+reward.name()+".";
            if(reward!=null) {
                if(reward.kind().equals("commodity")) cargo.addCommodity(reward.id(),1);
                else cargo.addSpecial(new SpecialItemData(reward.id(),reward.data().isEmpty()?null:reward.data()),1);
            }
            return result;
        }
    }
}
