package gamblingden.jackpot;

import java.util.*;
import org.json.*;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import gamblingden.Ids;
import gamblingden.economy.TokenBank;

/** Three independent reels. Only an exact three-symbol match awards one item. */
public final class JackpotGame {
    public static final String CONFIG_PATH="data/config/jackpot_rewards.json";
    public record Reward(String kind, String id, String data, String name, String icon, int minStake, float weight) {
        public String key() { return kind+":"+id+":"+data; }
    }
    private static List<Reward> cached;
    private static float symbolChance=.85f;
    private JackpotGame() { }
    public static void clearCache() { cached=null; }
    public static int clampStake(int stake) { return stake>=8?8:stake>=4?4:2; }
    public static int costOf(int stake) { return 3*clampStake(stake); }

    public static List<Reward> pool(int stake) {
        if(cached==null) load();
        int selected=clampStake(stake);
        return cached.stream().filter(r->r.minStake()<=selected).toList();
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
        double chance=json.optDouble("symbolChance",.85);
        symbolChance=(float)(Double.isFinite(chance)?Math.max(0,Math.min(1,chance)):.85);
        JSONArray entries=json.getJSONArray("rewards");
        for(int i=0;i<entries.length();i++) {
            try {
                JSONObject row=entries.getJSONObject(i);
                String kind=row.optString("kind","special"), id=row.getString("id"), data=row.optString("data","");
                double w=row.optDouble("weight",1);
                if(!Double.isFinite(w) || w<=0 || id.isBlank()) continue;
                int stake=clampStake(row.optInt("minStake",2));
                String name, icon;
                if(kind.equals("special")) {
                    var spec=Global.getSettings().getSpecialItemSpec(id);
                    if(spec==null || spec.hasTag("mission_item") || spec.hasTag("no_drop") || spec.hasTag("restricted")) continue;
                    name=spec.getName(); icon=spec.getIconName();
                } else if(kind.equals("commodity")) {
                    // Only AI cores are loose commodities in this machine, never trade goods.
                    int gate=switch(id) { case "gamma_core"->2; case "beta_core"->4; case "alpha_core"->8; default->0; };
                    if(gate==0) continue;
                    stake=Math.max(stake,gate);
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
        List<Reward> rewards=pool(stake);
        double total=rewards.stream().mapToDouble(Reward::weight).sum(), chance=0;
        if(total<=0) return 0;
        for(Reward r:rewards) chance+=Math.pow(symbolChance*r.weight()/total,3);
        return chance;
    }
    public static Round buy(int stake, Random random) {
        int cost=costOf(stake);
        if(TokenBank.getTokens()<cost) return null;
        List<Reward> rewards=pool(stake);
        if(rewards.isEmpty()) return null;
        WeightedRandomPicker<Reward> picker=new WeightedRandomPicker<>(random);
        for(Reward r:rewards) picker.add(r,r.weight());
        List<Reward> symbols=new ArrayList<>();
        for(int i=0;i<3;i++) symbols.add(random.nextFloat()<symbolChance?picker.pick():null);
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
