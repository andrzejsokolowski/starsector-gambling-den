package gamblingden.tests;

import java.lang.reflect.*;
import java.nio.IntBuffer;
import java.util.*;

import org.lwjgl.BufferUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.*;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.StaxDriver;
import com.fs.starfarer.api.*;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.WeaponAPI.*;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.loading.*;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.MutableValue;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.bar.*;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.*;
import gamblingden.*;
import gamblingden.den.DenDialog;
import gamblingden.economy.*;
import gamblingden.prizes.*;
import gamblingden.slots.*;

/** Runs in a separate JVM with mock campaign data; never connects to a live game or save. */
public class RegressionChecks {
    private static final Map<String, Object> saved = new HashMap<>(), memory = new HashMap<>();
    private static final List<HullModSpecAPI> hullmods = new ArrayList<>();
    private static final List<WeaponSpecAPI> weapons = new ArrayList<>();
    private static final List<FighterWingSpecAPI> fighterSpecs = new ArrayList<>();
    private static final Map<String,Integer> specialItems = new HashMap<>(), commodities = new HashMap<>();
    private static final Map<String,SpecialItemSpecAPI> specialSpecs = new HashMap<>();
    private static int storyPoints;
    private static final List<FleetMemberAPI> fleet = new ArrayList<>();
    private static final Set<String> known = new HashSet<>(), chips = new HashSet<>();
    private static final Map<String, Integer> guns = new HashMap<>(), wings = new HashMap<>();
    private static final Map<Object, String> options = new LinkedHashMap<>();
    private static final List<String> text = new ArrayList<>();
    private static final List<String> labelCaptions = new ArrayList<>();
    private static double credits;
    private static int assertions, dismissals;
    private static PositionAPI position;
    private static CargoAPI cargo;
    private static InteractionDialogPlugin currentDialog;
    private record DrawnIcon(String path, float x, float y, float alpha) { }
    private static final List<DrawnIcon> drawnIcons = new ArrayList<>();
    private static boolean recordIcons;
    private static boolean forbidRewards;
    private static int hullScans, cargoScans, weaponRollSetup, weaponWrites;

    private static void rewardWork() {
        if (forbidRewards) throw new AssertionError("Reward generation/cargo access while Pachinko is open");
    }

    interface Handler { Object call(Method method, Object[] args) throws Throwable; }
    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Handler handler) {
        Class<?>[] interfaces = type == LabelAPI.class ? new Class<?>[]{type, UIComponentAPI.class} : new Class<?>[]{type};
        return (T) Proxy.newProxyInstance(type.getClassLoader(), interfaces, (p,m,a) -> {
            if (m.getName().equals("toString")) return "mock " + type.getSimpleName();
            if (m.getName().equals("hashCode")) return System.identityHashCode(p);
            if (m.getName().equals("equals")) return p == a[0];
            Object value = handler.call(m, a == null ? new Object[0] : a);
            if (value != null) return value;
            Class<?> r = m.getReturnType();
            if (r == boolean.class) return false;
            if (r == int.class) return 0;
            if (r == float.class) return 0f;
            if (r == double.class) return 0d;
            if (r == long.class) return 0L;
            if (r.isInstance(p)) return p;
            return null;
        });
    }
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
    private static void set(Object instance, String name, Object value) throws Exception {
        Field f = instance.getClass().getDeclaredField(name); f.setAccessible(true); f.set(instance,value);
    }
    private static Object get(Object instance, String name) throws Exception {
        Field f = instance.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(instance);
    }
    private static void invoke(Object instance, String name, Class<?> type, Object arg) throws Exception {
        Method m = instance.getClass().getDeclaredMethod(name,type); m.setAccessible(true); m.invoke(instance,arg);
    }
    private static LabelAPI label() {
        String[] caption = {""};
        return proxy(LabelAPI.class,(m,a) -> switch(m.getName()) {
            case "setText" -> { caption[0] = (String)a[0]; yield null; }
            case "getText" -> caption[0];
            case "getPosition" -> position;
            default -> null;
        });
    }
    private static SpriteAPI sprite(String path) {
        float[] alpha={1f};
        return proxy(SpriteAPI.class,(m,a)->switch(m.getName()) {
            case "getTextureId" -> 1;
            case "getWidth", "getHeight" -> 80f;
            case "setAlphaMult" -> { alpha[0]=(Float)a[0]; yield null; }
            case "renderAtCenter" -> { drawnIcons.add(new DrawnIcon(path,(Float)a[0],(Float)a[1],alpha[0])); yield null; }
            default -> null;
        });
    }
    private static void reset() {
        saved.clear(); chips.clear(); known.clear(); hullmods.clear(); weapons.clear(); fleet.clear();
        guns.clear(); wings.clear(); text.clear(); options.clear(); credits=0; dismissals=0;
        drawnIcons.clear(); labelCaptions.clear(); recordIcons=false;
        forbidRewards=false; hullScans=cargoScans=weaponRollSetup=weaponWrites=0;
        WeaponPool.clearCache();
        FighterPool.clearCache(); fighterSpecs.clear(); specialItems.clear(); commodities.clear(); storyPoints=0;
    }
    private static void setup() {
        position = proxy(PositionAPI.class,(m,a) -> switch(m.getName()) {
            case "getX", "getY" -> 10f;
            case "getWidth" -> 1000f;
            case "getHeight" -> 660f;
            default -> null;
        });
        MutableValue account = new MutableValue() { @Override public void add(float value) { credits += value; } };
        cargo = proxy(CargoAPI.class,(m,a) -> switch(m.getName()) {
            case "getCredits" -> account;
            case "getStacksCopy" -> {
                rewardWork(); cargoScans++;
                List<CargoStackAPI> stacks = new ArrayList<>();
                for(String id:chips) stacks.add(proxy(CargoStackAPI.class,(sm,sa)->switch(sm.getName()) {
                    case "getSpecialDataIfSpecial" -> new SpecialItemData(Ids.MODSPEC,id);
                    case "getSize" -> 1f;
                    default -> null;
                }));
                yield stacks;
            }
            case "addSpecial" -> {
                rewardWork(); SpecialItemData item=(SpecialItemData)a[0];
                if(item.getId().equals(Ids.MODSPEC)) check(chips.add(item.getData()),"Duplicate blueprint awarded");
                else specialItems.merge(item.getId(),1,Integer::sum);
                yield null;
            }
            case "addCommodity" -> { commodities.merge((String)a[0],((Number)a[1]).intValue(),Integer::sum); yield null; }
            case "addWeapons" -> { rewardWork(); weaponWrites++; guns.merge((String)a[0],(Integer)a[1],Integer::sum); yield null; }
            case "addFighters" -> { wings.merge((String)a[0],(Integer)a[1],Integer::sum); yield null; }
            case "removeItems" -> { chips.remove(((SpecialItemData)a[1]).getData()); yield null; }
            default -> null;
        });
        FleetDataAPI data = proxy(FleetDataAPI.class,(m,a)->switch(m.getName()) {
            case "getMembersListCopy" -> new ArrayList<>(fleet);
            case "removeFleetMember" -> { fleet.remove(a[0]); yield null; }
            default -> null;
        });
        CampaignFleetAPI player = proxy(CampaignFleetAPI.class,(m,a)->switch(m.getName()) {
            case "getCargo" -> cargo;
            case "getFleetData" -> data;
            default -> null;
        });
        FactionAPI faction = proxy(FactionAPI.class,(m,a)->m.getName().equals("knowsHullMod") ? known.contains(a[0]) : null);
        var mem = proxy(com.fs.starfarer.api.campaign.rules.MemoryAPI.class,(m,a)->switch(m.getName()) {
            case "set" -> { memory.put((String)a[0],a[1]); yield null; }
            case "get" -> memory.get(a[0]);
            default -> null;
        });
        Global.setSector(proxy(SectorAPI.class,(m,a)->switch(m.getName()) {
            case "getPlayerStats" -> proxy(com.fs.starfarer.api.characters.MutableCharacterStatsAPI.class,(sm,sa)->switch(sm.getName()) {
                case "getStoryPoints" -> storyPoints;
                case "addStoryPoints" -> { storyPoints+=(Integer)sa[0];yield null; }
                default -> null;
            });
            case "getPersistentData" -> saved;
            case "getPlayerFleet" -> player;
            case "getPlayerFaction" -> faction;
            case "getMemoryWithoutUpdate" -> mem;
            default -> null;
        }));
        Global.setSettings(proxy(SettingsAPI.class,(m,a)->switch(m.getName()) {
            case "getAllHullModSpecs" -> { rewardWork(); hullScans++; yield new ArrayList<>(hullmods); }
            case "getAllWeaponSpecs" -> { rewardWork(); yield new ArrayList<>(weapons); }
            case "getAllFighterWingSpecs" -> new ArrayList<>(fighterSpecs);
            case "getSpecialItemSpec" -> specialSpecs.get(a[0]);
            case "getCommoditySpec" -> proxy(com.fs.starfarer.api.campaign.econ.CommoditySpecAPI.class,(sm,sa)->switch(sm.getName()) {
                case "getId","getName" -> a[0];
                case "getIconName" -> "graphics/icons/cargo/"+a[0]+".png";
                default -> null;
            });
            case "getMergedJSONForMod" -> readJson((String)a[0]);
            case "getAllBarEventSpecs" -> new ArrayList<>();
            case "getHullModSpec" -> hullmods.stream().filter(s->s.getId().equals(a[0])).findFirst().orElse(null);
            case "getScreenScaleMult" -> 1f;
            case "getSprite" -> recordIcons ? sprite((String)a[0]) : null;
            case "createLabel" -> { labelCaptions.add((String)a[0]); yield label(); }
            default -> null;
        }));
        Global.setSoundPlayer(proxy(SoundPlayerAPI.class,(m,a)->null));
    }
    private static HullModSpecAPI hullmod(String id, float rarity, String... tags) {
        return proxy(HullModSpecAPI.class,(m,a)->switch(m.getName()) {
            case "getId", "getDisplayName" -> id;
            case "getRarity" -> { rewardWork(); yield rarity; }
            case "hasTag" -> Arrays.asList(tags).contains(a[0]);
            case "getBaseValue" -> 2000f;
            default -> null;
        });
    }
    private static WeaponSpecAPI weapon(String id, float rarity, boolean system, String... tags) {
        return proxy(WeaponSpecAPI.class,(m,a)->switch(m.getName()) {
            case "getWeaponId", "getWeaponName" -> id;
            case "getRarity" -> { rewardWork(); weaponRollSetup++; yield rarity; }
            case "getType" -> WeaponType.ENERGY;
            case "getAIHints" -> system ? EnumSet.of(AIHints.SYSTEM) : EnumSet.noneOf(AIHints.class);
            case "hasTag" -> Arrays.asList(tags).contains(a[0]);
            default -> null;
        });
    }
    private static void reels() {
        for(Prize prize:Prize.values()) for(float dt:new float[]{1f/240,1f/60,1f/20,0.5f})
            for(int seed=0;seed<25;seed++) for(int skip:new int[]{-1,0,1,10,60}) {
                Reel r=new Reel(Arrays.asList(Prize.values()),84,620+seed,new Random(seed)); r.spinning=true;
                r.advance(seed/77f); r.stopOn(prize);
                for(int frame=0;frame<20000 && !r.stopped;frame++) { if(frame==skip) r.snapToResult(); r.advance(dt); }
                check(r.stopped && r.offset==0 && r.getPayLineSymbol()==prize,"Reel result mismatch");
            }
    }
    private static void prizes() {
        reset();
        hullmods.add(hullmod("available1",1)); hullmods.add(hullmod("available2",1));
        hullmods.add(hullmod("zero",0)); hullmods.add(hullmod("blocked",1,Tags.HULLMOD_NO_DROP));
        hullmods.add(hullmod("known",1)); known.add("known");
        hullmods.add(hullmod("held",1)); chips.add("held");
        Payout box=new Payout(); box.add(Prize.BOX_LARGE,1);
        check(box.describe().contains("2 hull mod blueprints") && box.describe().contains("40,000 credits"),"Partial exhaustion preview");
        var receipt=box.grant(new Random(1));
        check(receipt.getBlueprints()==2 && receipt.getCredits()==40000,"Partial exhaustion receipt");
        check(credits==40000 && chips.size()==3,"Actual cargo differs from receipt");
        box.grant(new Random(1)); check(credits==40000,"Prize paid twice");
        check(BlueprintPool.isExhausted(),"Zero rarity/no-drop/known/held exclusion");
        Payout emptyBox=new Payout(); emptyBox.add(Prize.BOX_SMALL,1);
        check(emptyBox.describe().contains("15,000 credits"),"Exhausted box preview");
        check(emptyBox.grant(new Random(1)).getBlueprints()==0,"Exhausted box awarded a blueprint");

        weapons.add(weapon("normal",1,false)); weapons.add(weapon("fighter",1,true));
        weapons.add(weapon("zero",0,false)); weapons.add(weapon("restricted",1,false,Tags.RESTRICTED));
        weapons.add(weapon("noDrop",1,false,Tags.NO_DROP)); weapons.add(weapon("noSalvage",1,false,"no_drop_salvage"));
        for(int i=0;i<100;i++) check(WeaponPool.pick(new Random(i)).getWeaponId().equals("normal"),"Invalid weapon selected");
        int oldCount=Prize.WEAPONS_LARGE.count;
        Prize.WEAPONS_LARGE.count=100;
        Payout crate=new Payout(); crate.add(Prize.WEAPONS_LARGE,1);
        Prize.WEAPONS_LARGE.count=1;
        check(crate.getWeaponCount()==100,"Won crate quantity changed with settings");
        check(crate.grant(new Random(1)).getWeapons()==100 && guns.get("normal")==100,"100-weapon crate");
        Prize.WEAPONS_LARGE.count=oldCount;

        Payout cash=new Payout(); cash.add(Prize.CREDITS,2000000);
        while(cash.canDouble()) check(cash.doubleUp(),"Safe double refused");
        String before=cash.describe(); check(!cash.doubleUp() && before.equals(cash.describe()),"Unsafe double changed prize");
        check(cash.grant(new Random(1)).getCredits()>0,"Negative doubled prize");
        saved.put(Ids.KEY_TOKENS,Integer.MAX_VALUE-5);
        check(TokenBank.addTokens(100)==5 && TokenBank.getTokens()==Integer.MAX_VALUE,"Token overflow");
        check(!TokenBank.spendTokens(-1),"Negative stake accepted");
    }
    private static ShipVariantAPI variant(Map<String,String> fittings, List<String> fighterWings,
                                           Map<String,ShipVariantAPI> modules, boolean damaged) {
        return proxy(ShipVariantAPI.class,(m,a)->switch(m.getName()) {
            case "getNonBuiltInWeaponSlots" -> new ArrayList<>(fittings.keySet());
            case "getWeaponId" -> fittings.get(a[0]);
            case "getNonBuiltInWings" -> fighterWings;
            case "getStationModules" -> { Map<String,String> ids=new HashMap<>(); modules.keySet().forEach(k->ids.put(k,k)); yield ids; }
            case "getModuleVariant" -> modules.get(a[0]);
            case "getHullMods" -> damaged ? List.of("dmod") : List.of();
            default -> null;
        });
    }
    private static FleetMemberAPI ship(String name, ShipVariantAPI variant, boolean flagship) {
        ShipHullSpecAPI hull=proxy(ShipHullSpecAPI.class,(m,a)->switch(m.getName()) {
            case "getHullNameWithDashClass" -> "Hammerhead-class";
            case "getFleetPoints" -> 10;
            default -> null;
        });
        return proxy(FleetMemberAPI.class,(m,a)->switch(m.getName()) {
            case "getShipName" -> name;
            case "getHullSpec" -> hull;
            case "getVariant" -> variant;
            case "getFleetPointCost" -> 10;
            case "isFlagship" -> flagship;
            default -> null;
        });
    }
    private static void ships() throws Exception {
        reset(); hullmods.add(hullmod("dmod",0,Tags.HULLMOD_DMOD));
        ShipVariantAPI module=variant(Map.of("mount","moduleGun"),List.of("moduleWing"),Map.of(),false);
        ShipVariantAPI fitted=variant(Map.of("one","gun","two","gun"),List.of("wing"),Map.of("module",module),false);
        FleetMemberAPI alpha=ship("Alpha",fitted,false), beta=ship("Beta",variant(Map.of(),List.of(),Map.of(),true),false);
        FleetMemberAPI flag=ship("Flagship",fitted,true); fleet.addAll(List.of(alpha,beta,flag));
        TextPanelAPI textPanel=proxy(TextPanelAPI.class,(m,a)-> { if(m.getName().equals("addPara")) text.add((String)a[0]); return null; });
        OptionPanelAPI optionPanel=proxy(OptionPanelAPI.class,(m,a)-> {
            if(m.getName().equals("clearOptions")) options.clear();
            if(m.getName().equals("addOption")) options.put(a[1],(String)a[0]); return null;
        });
        InteractionDialogAPI dialog=proxy(InteractionDialogAPI.class,(m,a)->switch(m.getName()) {
            case "getTextPanel" -> textPanel;
            case "getOptionPanel" -> optionPanel;
            case "setPlugin" -> { currentDialog=(InteractionDialogPlugin)a[0]; yield null; }
            default -> null;
        });
        DenDialog.open(dialog,new HashMap<>());
        invoke(currentDialog,"quoteShips",List.class,List.of(alpha,beta));
        check(fleet.size()==3 && TokenBank.getTokens()==0,"Quote sold ships");
        check(text.stream().anyMatch(s->s.contains("Alpha") && s.contains("15 tokens")),"Alpha quote missing");
        check(text.stream().anyMatch(s->s.contains("Beta") && s.contains("14 tokens")),"Beta quote missing");
        check(options.get("gd_confirm_sale").contains("29 tokens"),"Total quote mismatch");
        currentDialog.optionSelected("Cancel","gd_cancel_sale");
        check(fleet.size()==3 && guns.isEmpty(),"Cancel changed fleet/cargo");
        invoke(currentDialog,"quoteShips",List.class,List.of(alpha,beta));
        float previous=Config.SHIP_TOKENS_PER_FP; Config.SHIP_TOKENS_PER_FP=20;
        currentDialog.optionSelected("Sell","gd_confirm_sale"); Config.SHIP_TOKENS_PER_FP=previous;
        check(fleet.equals(List.of(flag)) && TokenBank.getTokens()==29,"Confirmed quote not honored");
        check(guns.get("gun")==2 && guns.get("moduleGun")==1 && wings.size()==2,"Fittings not recovered");
        check(ShipTradeIn.tradeIn(alpha)==0 && ShipTradeIn.tradeIn(flag)==0,"Stale ship/flagship sold");
        check(TokenBank.getTokens()==29 && guns.get("gun")==2,"Repeated sale paid twice");
    }
    private static SlotMachinePanel machine() throws Exception {
        return (SlotMachinePanel) machine(SlotMachinePanel.class);
    }
    private static BaseCustomUIPanelPlugin machine(Class<?> type) throws Exception {
        BaseCustomUIPanelPlugin panel=(BaseCustomUIPanelPlugin)type.getConstructor().newInstance();
        CustomPanelAPI ui=proxy(CustomPanelAPI.class,(m,a)->m.getName().equals("addComponent") ? position : null);
        var callbacks=proxy(CustomVisualDialogDelegate.DialogCallbacks.class,(m,a)->{
            if(m.getName().equals("dismissDialog")) dismissals++; return null;
        });
        type.getMethod("init",CustomPanelAPI.class,CustomVisualDialogDelegate.DialogCallbacks.class).invoke(panel,ui,callbacks);
        panel.positionChanged(position); return panel;
    }
    private static InputEventAPI key(int code) {
        return proxy(InputEventAPI.class,(m,a)->switch(m.getName()) {
            case "isKeyDownEvent" -> true;
            case "getEventValue" -> code;
            default -> null;
        });
    }
    private static void ui() throws Exception {
        for(String close:List.of("escape","leave","external")) {
            reset(); saved.put(Ids.KEY_TOKENS,100);
            SlotMachinePanel panel=machine(); panel.processInput(List.of(key(Keyboard.KEY_SPACE)));
            Payout prize=new Payout(); prize.add(Prize.CREDITS,20000);
            set(panel,"pending",new SpinResult(List.of(Prize.CREDITS,Prize.BUST,Prize.BUST),prize,false));
            if(close.equals("escape")) panel.processInput(List.of(key(Keyboard.KEY_ESCAPE)));
            if(close.equals("leave")) invoke(panel,"act",String.class,"leave");
            if(close.equals("external")) new SlotMachineDialogDelegate(panel,()->{}).reportDismissed(0);
            panel.finishOnDismissal(); panel.processInput(List.of(key(Keyboard.KEY_SPACE),key(Keyboard.KEY_D)));
            check(TokenBank.getTokens()==100-SlotMachine.costOf(Config.REELS_DEFAULT,0) && credits==20000,"Closing lost or repeated payout: "+close);
            check(TokenBank.getTotalPulls()==1,"Closing recorded spin twice");
        }
        reset(); saved.put(Ids.KEY_TOKENS,100);
        SlotMachinePanel panel=machine();
        check(labelCaptions.contains("SLOTS") && !labelCaptions.contains("GAMBLING DEN"),"Venue name is still used as the machine title");
        invoke(panel,"act",String.class,"reels:2"); invoke(panel,"act",String.class,"reels:3");
        for(int stake=0;stake<Config.STAKE_COUNT;stake++) invoke(panel,"act",String.class,"stake:"+stake);
        int checkedReels=0,checkedStakes=0;
        for(Object button:(List<?>)get(panel,"buttons")) {
            if((Boolean)get(button,"checked")) {
                String action=(String)get(button,"action");
                if(action.startsWith("reels:")) checkedReels++;
                if(action.startsWith("stake:")) checkedStakes++;
            }
        }
        check(checkedReels==1 && checkedStakes==1,"Selectors not exclusive");
        Pbuffer buffer=new Pbuffer(1200,800,new PixelFormat(),null);
        try {
            buffer.makeCurrent();
            for(int reelCount=1;reelCount<=5;reelCount++) {
              invoke(panel,"act",String.class,"reels:"+reelCount);
              for(boolean clipped:new boolean[]{false,true}) {
                if(clipped) GL11.glEnable(GL11.GL_SCISSOR_TEST); else GL11.glDisable(GL11.GL_SCISSOR_TEST);
                GL11.glScissor(15,20,1100,700); panel.renderBelow(1f);
                IntBuffer rect=BufferUtils.createIntBuffer(16); GL11.glGetInteger(GL11.GL_SCISSOR_BOX,rect);
                check(GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)==clipped && rect.get(0)==15 && rect.get(1)==20
                        && rect.get(2)==1100 && rect.get(3)==700,"Renderer leaked clipping state");
                check(GL11.glGetError()==GL11.GL_NO_ERROR,"Renderer produced an OpenGL error");
              }
            }
        } finally { buffer.destroy(); }
    }
    @SuppressWarnings("deprecation")
    private static void legacy() {
        reset();
        XStream xml=new XStream(new StaxDriver());
        XStream.setupDefaultSecurity(xml);
        xml.allowTypes(new Class<?>[]{hullmoddispenser.bar.DispenserBarEvent.class, gamblingden.bar.DenBarEvent.class});
        PortsideBarData bar=new PortsideBarData();
        BarEventManager manager=new BarEventManager();
        for(String name:List.of("hullmoddispenser.bar.DispenserBarEvent","gamblingden.bar.DenBarEvent")) {
            PortsideBarEvent old=(PortsideBarEvent)xml.fromXML("<"+name+"/>");
            check(old.shouldRemoveEvent() && !old.shouldShowAtMarket(null),"Legacy event remained active");
            bar.addEvent(old); manager.getActive().set(old,30f);
        }
        BaseBarEvent other=new BaseBarEvent(); bar.addEvent(other); manager.getActive().set(other,30f);
        LegacyBarCleanup.removeOldEvents();
        check(bar.getEvents().equals(List.of(other)) && manager.getActive().getItems().equals(List.of(other)),"Legacy cleanup removed wrong events");
        saved.put("hmd_tokens",42); TokenBank.migrateOldKeys();
        check(TokenBank.getTokens()==42 && !saved.containsKey("hmd_tokens"),"Token migration");
    }
    private static void rewardDisplay() throws Exception {
        Pbuffer buffer=new Pbuffer(1200,800,new PixelFormat(),null);
        try {
            buffer.makeCurrent();
            for(int stake=0;stake<Config.STAKE_COUNT;stake++) for(Prize featured:Prize.values())
              for(int count=1;count<=5;count++) for(int skip:new int[]{-1,0,90}) {
                reset(); recordIcons=true; saved.put(Ids.KEY_TOKENS,10000);
                for(int i=0;i<100;i++) hullmods.add(hullmod("display"+i,1));
                weapons.add(weapon("displayWeapon",1,false));
                fighterSpecs.add(fighter("displayWing",1));
                SlotMachinePanel panel=machine();
                invoke(panel,"act",String.class,"reels:"+count);
                invoke(panel,"act",String.class,"stake:"+stake);
                invoke(panel,"act",String.class,"pull");
                List<Prize> symbols=new ArrayList<>();
                Payout payout=new Payout();
                for(int i=0;i<count;i++) {
                    Prize prize=i==count/2 ? featured : Prize.values()[(i+count)%Prize.values().length];
                    symbols.add(prize); payout.add(prize,prize.isCrate() ? 1 : 100);
                }
                set(panel,"pending",new SpinResult(symbols,payout,false));
                // No native mouse device is needed for the campaign update callback.
                panel.positionChanged(null);
                for(int frame=0;frame<1800 && get(panel,"state").toString().equals("SPINNING");frame++) {
                    if(frame==skip) invoke(panel,"act",String.class,"skip");
                    panel.advance(1f/60f);
                }
                panel.positionChanged(position);
                check(!get(panel,"state").toString().equals("SPINNING"),"Display test never settled");
                List<?> reels=(List<?>)get(panel,"reels"), plates=(List<?>)get(panel,"plateLabels");
                for(int i=0;i<count;i++) {
                    check(((Reel)reels.get(i)).getPayLineSymbol()==symbols.get(i),"Landed symbol differs from payout symbol");
                    check(((LabelAPI)plates.get(i)).getText().equals(symbols.get(i)==Prize.BUST ? "-" : symbols.get(i).label),"Reel label differs from symbol");
                }
                String won=((LabelAPI)get(panel,"resultLabel")).getText();
                check(won.equals(payout.isEmpty() ? "Nothing on any reel." : "You won "+payout.describe()+"."),"Reward text differs from payout");
                // Capture the actual SpriteAPI calls, including the coordinates used to draw
                // the middle row, instead of only inspecting Reel.getPayLineSymbol().
                drawnIcons.clear(); panel.renderBelow(1f);
                float middleY=position.getY()+SlotMachinePanel.PANEL_H-158f-126f;
                List<DrawnIcon> middle=drawnIcons.stream().filter(icon->Math.abs(icon.y()-middleY)<0.01f).toList();
                List<String> expected=symbols.stream().filter(Prize::pays).map(prize->prize.icon).toList();
                check(middle.stream().map(DrawnIcon::path).toList().equals(expected),"Drawn middle-row icons differ from prizes");
                check(middle.stream().allMatch(icon->icon.alpha()==1f),"Reward row was dimmed");
                check(drawnIcons.stream().filter(icon->Math.abs(icon.y()-middleY)>0.01f).allMatch(icon->icon.alpha()<0.3f),"Decorative symbols look like paid symbols");
                if(!payout.isEmpty()) {
                    invoke(panel,"act",String.class,"take");
                    var receipt=payout.grant(new Random(1));
                    check(((LabelAPI)get(panel,"resultLabel")).getText().equals("Collected: "+receipt.describe()+"."),"Collected text differs from receipt");
                    check(chips.size()==receipt.getBlueprints() && guns.getOrDefault("displayWeapon",0)==receipt.getWeapons()
                            && wings.getOrDefault("displayWing",0)==receipt.getFighters() && storyPoints==receipt.getStoryPoints()
                            && credits==receipt.getCredits(),"Cargo differs from displayed receipt");
                    check(TokenBank.getTokens()==10000-SlotMachine.costOf(count,stake)+receipt.getTokens(),"Token cost or reward differs from the selected stake");
                }
            }
            // After a completed pull, changing settings must not pair new decorative icons
            // with the old result. Clicking the current selection must not reroll them either.
            for(String setting:List.of("stake:1","reels:2")) {
                reset(); saved.put(Ids.KEY_TOKENS,1000);
                SlotMachinePanel panel=machine();
                invoke(panel,"act",String.class,"pull");
                Payout payout=new Payout(); payout.add(Prize.CREDITS,777);
                set(panel,"pending",new SpinResult(List.of(Prize.BUST,Prize.CREDITS,Prize.BUST),payout,false));
                invoke(panel,"act",String.class,"skip");
                invoke(panel,"settle",new Class<?>[0],new Object[0]);
                invoke(panel,"act",String.class,"take");
                String collected=((LabelAPI)get(panel,"resultLabel")).getText();
                Object reel=((List<?>)get(panel,"reels")).get(0);
                invoke(panel,"act",String.class,"stake:0"); invoke(panel,"act",String.class,"reels:3");
                check(((List<?>)get(panel,"reels")).get(0)==reel && ((LabelAPI)get(panel,"resultLabel")).getText().equals(collected),"Current selection rerolled icons beside old reward text");
                invoke(panel,"act",String.class,setting);
                check(((LabelAPI)get(panel,"resultLabel")).getText().isEmpty(),"Settings change left stale reward text");
                for(Object plate:(List<?>)get(panel,"plateLabels")) check(((LabelAPI)plate).getText().isEmpty(),"Settings change left stale reel labels");
                check(get(panel,"pending")==null && (Float)get(panel,"winGlow")==0f && (Float)get(panel,"shake")==0f,"Settings change retained previous win state");
                check(credits==777,"Changing settings changed collected payout");
            }
        } finally { buffer.destroy(); recordIcons=false; }
    }

    private static void invoke(Object instance, String name, Class<?>[] types, Object[] args) throws Exception {
        Method method=instance.getClass().getDeclaredMethod(name,types); method.setAccessible(true); method.invoke(instance,args);
    }
    private static void odds() {
        reset(); fighterSpecs.add(fighter("oddsWing",1));
        double[] expected={.0192,.048,.0832,.128};
        for(int stake=0;stake<Config.STAKE_COUNT;stake++) {
            Random random=new Random(30+stake); int boxes=0; int count=100000;
            for(int i=0;i<count;i++) if(SlotMachine.pull(1,stake,random).symbols.get(0).isBox()) boxes++;
            check(Math.abs((double)boxes/count-expected[stake])<.003,"Unexpected hullmod odds at stake "+stake);
            System.out.printf("Hullmod box rate, stake %d: %.2f%%%n",stake,100d*boxes/count);
        }
    }
    private static void stakes() throws Exception {
        reset();
        int[] costs={1,2,4,8};
        check(Config.STAKE_COUNT==4 && Arrays.equals(Config.STAKE_COST,costs),"Stake levels or prices differ from 1/2/4/8");
        for(int stake=0;stake<costs.length;stake++) {
            check(!Config.WEIGHTS[stake].isEmpty(),"Fourth stake has no prize pool");
            TokenBank.setStake(stake); check(TokenBank.getStake()==stake,"Stake preference was not preserved");
            for(int reels=1;reels<=5;reels++) check(SlotMachine.costOf(reels,stake)==costs[stake]*reels,"Wrong cost per reel");
        }
        SlotMachinePanel panel=machine();
        List<?> buttons=(List<?>)get(panel,"buttons");
        int count=0;
        float previousRight=0f;
        for(Object button:buttons) {
            if(!((String)get(button,"action")).startsWith("stake:")) continue;
            float x=(Float)get(button,"x"), width=(Float)get(button,"w");
            check(x>=previousRight && x+width<=SlotMachinePanel.PANEL_W,"Four stake buttons overlap or leave the panel");
            previousRight=x+width; count++;
        }
        check(count==4,"Fourth stake button is missing");
        check(labelCaptions.containsAll(List.of("1 token","2 tokens","4 tokens","8 tokens")),"Stake buttons do not show their per-reel costs");
        saved.put(Ids.KEY_TOKENS,39);
        invoke(panel,"act",String.class,"reels:5"); invoke(panel,"act",String.class,"stake:3");
        panel.processInput(List.of(key(Keyboard.KEY_SPACE)));
        check(TokenBank.getTokens()==39 && get(panel,"pending")==null,"Unaffordable 40-token spin was allowed");
        TokenBank.addTokens(1); panel.processInput(List.of(key(Keyboard.KEY_SPACE)));
        check(TokenBank.getTokens()==0 && ((SpinResult)get(panel,"pending")).symbols.size()==5,"Max stake did not charge exactly 40 tokens");
        panel.finishOnDismissal();
    }
    private static void pachinko() throws Exception {
        int oldBox = Prize.BOX_SMALL.count, oldWeapons = Prize.WEAPONS_SMALL.count;
        try {
            Prize.BOX_SMALL.count = Prize.WEAPONS_SMALL.count = 100;
            for (var category : gamblingden.pachinko.PachinkoSettings.Category.values()) {
                boolean[] seen = new boolean[11];
                for (int seed = 0; seed < 1200; seed++) {
                    reset(); TokenBank.addTokens(1000);
                    for (int i = 0; i < 40; i++) hullmods.add(hullmod("pachinko_" + i, 1));
                    weapons.add(weapon("test_gun", 1, false));
                    var offer = gamblingden.pachinko.PachinkoRound.offer(category);
                    int[] expected = {16,8,4,2,1,0,1,2,4,8,16};
                    for (int i=0;i<11;i++) check(offer.amount(i)==expected[i], "Wrong pachinko pocket layout");
                    var round = gamblingden.pachinko.PachinkoRound.buy(offer, new Random(seed * 7919L));
                    check(round != null && TokenBank.getTokens()==1000-offer.cost, "Ball not charged exactly once");
                    round.advance(1f/60); round.finish(); round.finish(); round.advance(20);
                    int pocket = round.board.getPocket(); seen[pocket] = true;
                    int amount = offer.amount(pocket);
                    check(chips.isEmpty() && guns.isEmpty() && TokenBank.getTokens()==1000-offer.cost+(category.name().equals("TOKENS")?amount:0),
                            "Landing wrote items or delayed tokens");
                    var receipt = round.getWinnings().collect(new Random(seed));
                    check(round.getWinnings().collect(new Random(seed+1))==receipt,"Collection did not return the same receipt");
                    check(chips.size() == (category.name().equals("HULLMODS") ? amount : 0), "Pachinko hullmod mismatch/crate multiplication");
                    check(guns.getOrDefault("test_gun",0) == (category.name().equals("WEAPONS") ? amount : 0), "Pachinko weapon mismatch/crate multiplication");
                    check(TokenBank.getTokens()==1000-offer.cost+(category.name().equals("TOKENS")?amount:0), "Pachinko token mismatch");
                    check(credits==0, "Targeted prize unexpectedly converted to credits");
                    check(amount==0 ? round.getResult().equals("Nothing.") : round.getResult().startsWith("Won "+amount+" "), "Result disagrees with pocket");
                }
                for(boolean pocket:seen) check(pocket,"Payout category never reached a pocket");
            }
        } finally { Prize.BOX_SMALL.count = oldBox; Prize.WEAPONS_SMALL.count = oldWeapons; }

        reset(); hullmods.add(hullmod("last",1)); TokenBank.addTokens(100);
        var cap = gamblingden.pachinko.PachinkoRound.offer(gamblingden.pachinko.PachinkoSettings.Category.HULLMODS);
        check(cap.maximum()==1 && !cap.notice.isEmpty(), "Hullmod shortage was not visible before buying");
        known.add("last");
        check(gamblingden.pachinko.PachinkoRound.buy(cap,new Random(3))==null && TokenBank.getTokens()==100, "Stale quote charged tokens");
        var empty = gamblingden.pachinko.PachinkoRound.offer(gamblingden.pachinko.PachinkoSettings.Category.HULLMODS);
        check(!empty.canBuy() && empty.maximum()==0,"Exhausted hullmod board still accepts bets");
        var noWeapons = gamblingden.pachinko.PachinkoRound.offer(gamblingden.pachinko.PachinkoSettings.Category.WEAPONS);
        check(!noWeapons.canBuy(),"Empty weapon board accepts bets");
        reset();
        var noTokens = gamblingden.pachinko.PachinkoRound.offer(gamblingden.pachinko.PachinkoSettings.Category.TOKENS);
        check(gamblingden.pachinko.PachinkoRound.buy(noTokens,new Random(2))==null && TokenBank.getTokens()==0,"Unaffordable ball was created");

        for (String close:List.of("leave","escape","external")) {
            reset(); TokenBank.addTokens(100);
            var panel=(gamblingden.pachinko.PachinkoPanel)machine(gamblingden.pachinko.PachinkoPanel.class);
            // Click real hitboxes: category selector, Drop, attempted mid-flight selector.
            invoke(panel,"pointer",new Class<?>[]{float.class,float.class,boolean.class},new Object[]{600f,85f,true});
            invoke(panel,"pointer",new Class<?>[]{float.class,float.class,boolean.class},new Object[]{600f,85f,false});
            invoke(panel,"pointer",new Class<?>[]{float.class,float.class,boolean.class},new Object[]{140f,623f,true});
            var round=(gamblingden.pachinko.PachinkoRound)get(panel,"round");
            check(round!=null && TokenBank.getTokens()==98,"Pachinko mouse Drop does not work");
            invoke(panel,"pointer",new Class<?>[]{float.class,float.class,boolean.class},new Object[]{140f,623f,true});
            check(get(panel,"round")==round && TokenBank.getTokens()==98,"Held mouse bought extra balls");
            invoke(panel,"act",String.class,"category:WEAPONS");
            check(get(panel,"category")==gamblingden.pachinko.PachinkoSettings.Category.TOKENS,"Category changed mid-flight");
            round.board.finish();
            int amount=round.offer.amount(round.board.getPocket());
            if(close.equals("leave")) invoke(panel,"act",String.class,"leave");
            if(close.equals("escape")) panel.processInput(List.of(key(Keyboard.KEY_ESCAPE)));
            if(close.equals("external")) {
                int[] calls={0};
                var delegate=new gamblingden.pachinko.PachinkoDialogDelegate(panel,()->calls[0]++);
                delegate.reportDismissed(0); delegate.reportDismissed(0);
                check(calls[0]==1,"Pachinko close callback repeated");
            }
            panel.finishOnDismissal(); invoke(panel,"act",String.class,"drop"); invoke(panel,"act",String.class,"skip");
            check(TokenBank.getTokens()==98+amount && round.isSettled(),"Closing lost/repeated paid ball");
            check(((LabelAPI)get(panel,"result")).getText().equals(round.getResult()),"Panel result does not match actual receipt");
        }
        reset(); TokenBank.addTokens(100);
        var panel=(gamblingden.pachinko.PachinkoPanel)machine(gamblingden.pachinko.PachinkoPanel.class);
        invoke(panel,"act",String.class,"category:TOKENS"); invoke(panel,"act",String.class,"drop"); invoke(panel,"act",String.class,"skip");
        var landed=get(panel,"round");
        invoke(panel,"act",String.class,"category:TOKENS");
        check(get(panel,"round")==landed,"Same category cleared the paid result");
        invoke(panel,"act",String.class,"category:WEAPONS");
        check(get(panel,"round")==null && ((LabelAPI)get(panel,"result")).getText().isEmpty(),"Changed category retained stale result");
        int checked=0;
        for(Object button:(List<?>)get(panel,"buttons")) {
            if((Boolean)get(button,"checked")) checked++;
            check((Float)get(button,"x")>=0 && (Float)get(button,"x")+(Float)get(button,"w")<=1000
                    && (Float)get(button,"y")+(Float)get(button,"h")<=660,"Pachinko button outside panel");
        }
        check(checked==1,"Pachinko categories are not mutually exclusive");
        Pbuffer buffer=new Pbuffer(1200,800,new PixelFormat(),null);
        try {
            buffer.makeCurrent();
            for(boolean clip:new boolean[]{false,true}) {
                if(clip) GL11.glEnable(GL11.GL_SCISSOR_TEST); else GL11.glDisable(GL11.GL_SCISSOR_TEST);
                GL11.glScissor(15,20,1100,700); panel.renderBelow(1);
                IntBuffer rect=BufferUtils.createIntBuffer(16); GL11.glGetInteger(GL11.GL_SCISSOR_BOX,rect);
                check(GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)==clip && rect.get(0)==15 && rect.get(1)==20
                        && rect.get(2)==1100 && rect.get(3)==700,"Pachinko leaked clipping state");
                check(GL11.glGetError()==GL11.GL_NO_ERROR,"Pachinko native render error");
            }
        } finally { buffer.destroy(); }
    }

    private static void pachinkoSettings() throws Exception {
        var loader=lunalib.backend.ui.settings.LunaSettingsLoader.INSTANCE;
        boolean loaded=loader.getHasLoaded();
        var previous=lunalib.backend.ui.settings.LunaSettingsLoader.getSettings();
        // In-memory only: never call save/close on this settings object.
        var settings=new org.lazywizard.lazylib.JSONUtils.CommonDataJSONObject("unused-test-settings");
        for(String line:java.nio.file.Files.readAllLines(java.nio.file.Path.of("data/config/LunaSettings.csv"))) {
            if(!line.startsWith("gd_pachinko_")) continue;
            String[] columns=line.split(",",-1);
            check(columns.length==9,"Malformed pachinko Luna settings row");
            if(columns[2].equals("Int")) settings.put(columns[0],Integer.parseInt(columns[3]));
        }
        try {
            loader.setHasLoaded(true);
            lunalib.backend.ui.settings.LunaSettingsLoader.setSettings(new HashMap<>(Map.of(Ids.MOD_ID,settings)));
            reset(); TokenBank.addTokens(2000); weapons.add(weapon("custom_gun",1,false));
            for(int i=0;i<6;i++) settings.put("gd_pachinko_pocket_"+i,100);
            settings.put("gd_pachinko_cost_weapons",7);
            var offer=gamblingden.pachinko.PachinkoRound.offer(gamblingden.pachinko.PachinkoSettings.Category.WEAPONS);
            check(offer.maximum()==100 && offer.cost==7,"Luna custom values ignored");
            var round=gamblingden.pachinko.PachinkoRound.buy(offer,new Random(12));
            check(round!=null && TokenBank.getTokens()==1993,"Custom ball cost wrong");
            for(int i=0;i<6;i++) settings.put("gd_pachinko_pocket_"+i,1);
            settings.put("gd_pachinko_cost_weapons",100);
            round.finish(); round.finish();
            check(guns.isEmpty() && round.getWinnings().weapons()==100,"Settings changed held reward or paid it early");
            round.getWinnings().collect(new Random(12));
            check(guns.get("custom_gun")==100 && TokenBank.getTokens()==1993,"In-flight settings changed paid ball");
            check(gamblingden.pachinko.PachinkoRound.buy(offer,new Random(12))==null && TokenBank.getTokens()==1993,"Changed settings silently charged stale quote");
            settings.put("gd_pachinko_cost_weapons",-5);
            settings.put("gd_pachinko_pocket_5",Integer.MAX_VALUE);
            settings.put("gd_pachinko_pocket_0",-1);
            var bounded=gamblingden.pachinko.PachinkoRound.offer(gamblingden.pachinko.PachinkoSettings.Category.WEAPONS);
            check(bounded.cost==1 && bounded.amount(0)==100 && bounded.amount(5)==0,"Unsafe settings not clamped");

            reset(); TokenBank.addTokens(Integer.MAX_VALUE);
            var capped=gamblingden.pachinko.PachinkoRound.offer(gamblingden.pachinko.PachinkoSettings.Category.TOKENS);
            check(capped.maximum()<=capped.cost && !capped.notice.isEmpty(),"Token cap not advertised");
            var limit=gamblingden.pachinko.PachinkoRound.buy(capped,new Random(4)); limit.finish();
            limit.getWinnings().collect(new Random(4));
            check(TokenBank.getTokens()==Integer.MAX_VALUE-capped.cost+capped.amount(limit.board.getPocket()),"Token cap mismatch");

            reset(); TokenBank.addTokens(100); hullmods.add(hullmod("disappears",1));
            for(int i=0;i<6;i++) settings.put("gd_pachinko_pocket_"+i,1);
            var disappearing=gamblingden.pachinko.PachinkoRound.buy(
                    gamblingden.pachinko.PachinkoRound.offer(gamblingden.pachinko.PachinkoSettings.Category.HULLMODS),new Random(7));
            known.add("disappears"); disappearing.finish(); disappearing.finish();
            check(TokenBank.getTokens()==100-disappearing.offer.cost,"External stock change paid an early refund");
            var missingReceipt=disappearing.getWinnings().collect(new Random(7));
            disappearing.getWinnings().collect(new Random(7));
            check(TokenBank.getTokens()==100 && chips.isEmpty() && credits==0 && missingReceipt.getRefunds()==disappearing.offer.cost,
                    "Unavailable target was not refunded exactly once on exit");

            reset(); TokenBank.addTokens(10000); weapons.add(weapon("bulk",1,false));
            for(int i=0;i<6;i++) settings.put("gd_pachinko_pocket_"+i,100);
            settings.put("gd_pachinko_cost_weapons",7);
            var liveBoard=(gamblingden.pachinko.PachinkoPanel)machine(gamblingden.pachinko.PachinkoPanel.class);
            invoke(liveBoard,"act",String.class,"category:WEAPONS");
            invoke(liveBoard,"act",String.class,"drop10");
            for(int i=0;i<6;i++) settings.put("gd_pachinko_pocket_"+i,1);
            settings.put("gd_pachinko_cost_weapons",1000);
            invoke(liveBoard,"act",String.class,"drop50");
            check(TokenBank.getTokens()==9580,"Live board did not preserve displayed price for additional balls");
            invoke(liveBoard,"act",String.class,"skip");
            var held=(gamblingden.pachinko.PachinkoWinnings)get(liveBoard,"winnings");
            check(guns.isEmpty() && held.weapons()==6000 && TokenBank.getTokens()==9580,"Multi-ball 100-item setting lost or multiplied pending rewards");
            invoke(liveBoard,"act",String.class,"drop");
            check(TokenBank.getTokens()==9580 && ((List<?>)get(liveBoard,"rounds")).isEmpty(),"Next run silently accepted changed settings");
            invoke(liveBoard,"act",String.class,"drop"); invoke(liveBoard,"act",String.class,"skip");
            check(TokenBank.getTokens()==8580 && guns.isEmpty() && held.weapons()==6001,"Next run lost held rewards or did not use fresh settings");
            liveBoard.finishOnDismissal(); liveBoard.finishOnDismissal();
            check(guns.get("bulk")==6001,"Changed settings altered already-won rewards on collection");
            pachinkoDeferred(settings);
            pachinkoWeaponDraws(settings);
        } finally {
            lunalib.backend.ui.settings.LunaSettingsLoader.setSettings(previous); loader.setHasLoaded(loaded);
        }
    }

    private static void pachinkoDeferred(org.lazywizard.lazylib.JSONUtils.CommonDataJSONObject settings) throws Exception {
        reset(); TokenBank.addTokens(100000);
        for(int i=0;i<3;i++) hullmods.add(hullmod("held_"+i,1));
        weapons.add(weapon("pooled_gun",1,false));
        settings.put("gd_pachinko_cost_hullmods",4);
        settings.put("gd_pachinko_cost_weapons",7);
        settings.put("gd_pachinko_cost_tokens",2);
        for(int i=0;i<6;i++) settings.put("gd_pachinko_pocket_"+i,2);
        var panel=(gamblingden.pachinko.PachinkoPanel)machine(gamblingden.pachinko.PachinkoPanel.class);
        var held=(gamblingden.pachinko.PachinkoWinnings)get(panel,"winnings");
        try {
            // Any inventory access, candidate scan, or item-picker setup now fails immediately.
            forbidRewards=true;
            invoke(panel,"act",String.class,"drop50");
            for(int frame=0;frame<600 && !((List<?>)get(panel,"rounds")).isEmpty();frame++) panel.advance(1f/60);
            invoke(panel,"act",String.class,"skip");
            check(held.blueprints()==2 && held.hullmodsLeft()==1 && held.refunds()==196,"Deferred scarcity reservations/refunds wrong");
            check(TokenBank.getTokens()==99996,"Stock refunds were not paid on landing");
            invoke(panel,"act",String.class,"drop"); // Refresh capped pockets from two to one, without charging.
            check(TokenBank.getTokens()==99996 && ((List<?>)get(panel,"rounds")).isEmpty(),"New run charged a stale stock quote");
            invoke(panel,"act",String.class,"drop"); invoke(panel,"act",String.class,"skip");
            check(held.blueprints()==3 && held.hullmodsLeft()==0,"New run reused reserved hullmods");
            invoke(panel,"act",String.class,"drop");
            check(TokenBank.getTokens()==99992,"Exhausted reserved stock accepted another bet");

            for(int i=0;i<6;i++) settings.put("gd_pachinko_pocket_"+i,100);
            invoke(panel,"act",String.class,"category:WEAPONS");
            for(int run=0;run<2;run++) {
                invoke(panel,"act",String.class,"drop50"); invoke(panel,"act",String.class,"drop50");
                invoke(panel,"act",String.class,"skip");
            }
            check(held.weapons()==20000 && held.blueprints()==3,"New runs/categories lost or capped pooled winnings");
            invoke(panel,"act",String.class,"category:TOKENS");
            invoke(panel,"act",String.class,"drop50"); invoke(panel,"act",String.class,"skip");
            check(held.tokens()==5000 && TokenBank.getTokens()==103492,"Token winnings delayed until exit");
            check(chips.isEmpty() && guns.isEmpty() && panel.getSessionLog().isEmpty(),"Open screen generated or logged item rewards");
            String display=((LabelAPI)get(panel,"pendingWinnings")).getText();
            check(display.contains("3 blueprints") && display.contains("20000 weapons") && !display.contains("tokens"),
                    "Pending label lost winnings after category changes");
            check(held.refunds()==196 && held.tokenRoom()==(long)Integer.MAX_VALUE-98296-5196,"Pending token/refund cap accounting wrong");
        } finally { forbidRewards=false; }

        hullScans=cargoScans=weaponRollSetup=weaponWrites=0;
        invoke(panel,"pointer",new Class<?>[]{float.class,float.class,boolean.class},new Object[]{870f,623f,false});
        invoke(panel,"pointer",new Class<?>[]{float.class,float.class,boolean.class},new Object[]{870f,623f,true});
        panel.finishOnDismissal();
        new gamblingden.pachinko.PachinkoDialogDelegate(panel,()->{}).reportDismissed(0);
        check(chips.size()==3 && guns.get("pooled_gun")==20000 && TokenBank.getTokens()==103492 && credits==0,
                "Leave & collect lost/capped/doubled mixed-category winnings");
        check(hullScans==1 && cargoScans==1 && weaponRollSetup==1 && weaponWrites==1,
                "Collection did not scan once and add matching weapons in bulk");
        check(panel.getSessionLog().size()==3 && held.isCollected(),"Collection receipt did not describe the entire visit");
        var receipt=held.collect(new Random(9));
        check(receipt.getBlueprints()==3 && receipt.getWeapons()==20000 && receipt.getTokens()==5196 && receipt.getRefunds()==196,
                "Collection receipt differs from cargo/balance");
        invoke(panel,"act",String.class,"drop50");
        check(TokenBank.getTokens()==103492 && guns.get("pooled_gun")==20000,"Closed panel accepted a purchase");
        var nextVisit=(gamblingden.pachinko.PachinkoPanel)machine(gamblingden.pachinko.PachinkoPanel.class);
        check(((gamblingden.pachinko.PachinkoWinnings)get(nextVisit,"winnings")).describe().isEmpty(),"New visit inherited old winnings");
        nextVisit.finishOnDismissal();
        check(TokenBank.getTokens()==103492 && guns.get("pooled_gun")==20000,"Empty visit paid old winnings again");

        reset(); TokenBank.addTokens(2);
        var broke=(gamblingden.pachinko.PachinkoPanel)machine(gamblingden.pachinko.PachinkoPanel.class);
        invoke(broke,"act",String.class,"category:TOKENS");
        invoke(broke,"act",String.class,"drop"); invoke(broke,"act",String.class,"skip");
        check(TokenBank.getTokens()==100 && ((gamblingden.pachinko.PachinkoWinnings)get(broke,"winnings")).tokens()==100,
                "Token reward was not immediately spendable");
        invoke(broke,"act",String.class,"drop");
        check(((List<?>)get(broke,"rounds")).size()==1 && TokenBank.getTokens()==98,"Cannot buy another ball with token winnings");
        broke.finishOnDismissal();
        check(TokenBank.getTokens()==198,"Leaving lost or repeated token winnings");

        // External stock changes are reconciled only at collection, never during a fall.
        reset(); TokenBank.addTokens(100);
        for(int i=0;i<4;i++) hullmods.add(hullmod("changed_"+i,1));
        for(int i=0;i<6;i++) settings.put("gd_pachinko_pocket_"+i,2);
        var batch=gamblingden.pachinko.PachinkoRound.buyBatch(gamblingden.pachinko.PachinkoRound.offer(
                gamblingden.pachinko.PachinkoSettings.Category.HULLMODS),2,new Random(4),false);
        for(var ball:batch) ball.finish();
        known.add("changed_0");
        check(chips.isEmpty() && TokenBank.getTokens()==92,"Held hullmod lot paid early");
        var adjusted=batch.get(0).getWinnings().collect(new Random(4));
        check(adjusted.getBlueprints()==2 && adjusted.getRefunds()==4 && chips.size()==2 && !chips.contains("changed_0")
                && TokenBank.getTokens()==96 && credits==0,"Exit stock recheck partially substituted a hullmod lot");

        reset(); TokenBank.addTokens(100); weapons.add(weapon("removed_gun",1,false));
        var removed=gamblingden.pachinko.PachinkoRound.buy(gamblingden.pachinko.PachinkoRound.offer(
                gamblingden.pachinko.PachinkoSettings.Category.WEAPONS),new Random(4));
        removed.finish(); weapons.clear(); WeaponPool.clearCache();
        var refunded=removed.getWinnings().collect(new Random(4));
        check(refunded.getWeapons()==0 && refunded.getRefunds()==7 && guns.isEmpty() && TokenBank.getTokens()==100 && credits==0,
                "Missing weapon pool was not refunded on exit");
    }

    private static void pachinkoWeaponDraws(org.lazywizard.lazylib.JSONUtils.CommonDataJSONObject settings) throws Exception {
        Set<String> combinations=new HashSet<>();
        for(int seed=0;seed<200;seed++) {
            reset(); TokenBank.addTokens(100);
            for(int i=0;i<20;i++) weapons.add(weapon("random_"+i,i==0?1000000:1,false));
            weapons.add(weapon("forbidden",1000000,false,Tags.NO_DROP));
            for(int i=0;i<6;i++) settings.put("gd_pachinko_pocket_"+i,4);
            var ball=gamblingden.pachinko.PachinkoRound.buy(gamblingden.pachinko.PachinkoRound.offer(
                    gamblingden.pachinko.PachinkoSettings.Category.WEAPONS),new Random(seed));
            try { forbidRewards=true; ball.finish(); } finally { forbidRewards=false; }
            check(guns.isEmpty(),"Weapons generated on landing");
            var receipt=ball.getWinnings().collect(new Random(seed));
            check(receipt.getWeapons()==4 && guns.size()==4 && guns.values().stream().allMatch(n->n==1)
                    && !guns.containsKey("forbidden"),"Four-weapon reward repeated a weapon or ignored filters");
            ball.getWinnings().collect(new Random(seed+1));
            check(guns.values().stream().mapToInt(n->n).sum()==4,"Weapon award repeated on collection");
            combinations.add(new TreeSet<>(guns.keySet()).toString());
        }
        check(combinations.size()>30,"Weapon selections did not vary across rolls");
        for(int balls:new int[]{1,2}) {
            reset(); TokenBank.addTokens(100);
            for(int i=0;i<3;i++) weapons.add(weapon("tiny_"+i,1,false));
            for(int i=0;i<6;i++) settings.put("gd_pachinko_pocket_"+i,8);
            var batch=gamblingden.pachinko.PachinkoRound.buyBatch(gamblingden.pachinko.PachinkoRound.offer(
                    gamblingden.pachinko.PachinkoSettings.Category.WEAPONS),balls,new Random(33),false);
            for(var ball:batch) ball.finish();
            batch.get(0).getWinnings().collect(new Random(10));
            check(guns.size()==3 && guns.values().stream().mapToInt(n->n).sum()==8*balls
                    && guns.values().stream().allMatch(n->n>=2*balls && n<=3*balls),"Small weapon pool did not cycle before repeats");
        }
    }

    private static void blackjackUI() throws Exception {
        for(String close:List.of("leave","escape","external")) {
            reset(); TokenBank.addTokens(100);
            var panel=(gamblingden.blackjack.BlackjackPanel)machine(gamblingden.blackjack.BlackjackPanel.class);
            var game=(gamblingden.blackjack.BlackjackGame)get(panel,"game");
            BlackjackChecks.rig(game,8,10,8,6,10,6,10,5);
            invoke(panel,"pointer",new Class<?>[]{float.class,float.class,boolean.class},new Object[]{110f,625f,true});
            check(game.playing() && TokenBank.getTokens()==90,"Blackjack Deal mouse button failed");
            invoke(panel,"pointer",new Class<?>[]{float.class,float.class,boolean.class},new Object[]{110f,625f,true});
            check(TokenBank.getTokens()==90,"Held mouse dealt twice");
            invoke(panel,"act",String.class,"bet:50"); check((Integer)get(panel,"bet")==10,"Mid-hand bet changed");
            invoke(panel,"pointer",new Class<?>[]{float.class,float.class,boolean.class},new Object[]{730f,625f,false});
            invoke(panel,"pointer",new Class<?>[]{float.class,float.class,boolean.class},new Object[]{730f,625f,true});
            check(game.hands().size()==2 && TokenBank.getTokens()==80,"Blackjack Split mouse button failed");
            if(close.equals("leave")) {
                invoke(panel,"pointer",new Class<?>[]{float.class,float.class,boolean.class},new Object[]{900f,625f,false});
                invoke(panel,"pointer",new Class<?>[]{float.class,float.class,boolean.class},new Object[]{900f,625f,true});
            } else if(close.equals("escape")) panel.processInput(List.of(key(Keyboard.KEY_ESCAPE)));
            else {
                int[] calls={0}; var delegate=new gamblingden.blackjack.BlackjackDialogDelegate(panel,()->calls[0]++);
                delegate.reportDismissed(0); delegate.reportDismissed(0); check(calls[0]==1,"Blackjack duplicate close callback");
            }
            check(game.state()==gamblingden.blackjack.BlackjackGame.State.RESULT && TokenBank.getTokens()==120,"Blackjack exit lost split winnings");
            panel.finishOnDismissal(); invoke(panel,"act",String.class,"deal");
            check(TokenBank.getTokens()==120 && game.rounds()==1,"Blackjack exit repeated payout or accepted another bet");
            check(panel.getSessionSummary().contains("+20 tokens"),"Blackjack session message wrong");
        }
        reset(); TokenBank.addTokens(100);
        var panel=(gamblingden.blackjack.BlackjackPanel)machine(gamblingden.blackjack.BlackjackPanel.class);
        invoke(panel,"act",String.class,"bet:2"); invoke(panel,"act",String.class,"bet:20");
        int checked=0;
        for(Object button:(List<?>)get(panel,"buttons")) {
            if((Boolean)get(button,"checked")) checked++;
            check((Float)get(button,"x")>=0 && (Float)get(button,"x")+(Float)get(button,"w")<=1000
                    && (Float)get(button,"y")+(Float)get(button,"h")<=660,"Blackjack button outside panel");
        }
        check(checked==1,"Blackjack bets not mutually exclusive");
        var game=(gamblingden.blackjack.BlackjackGame)get(panel,"game");
        BlackjackChecks.rig(game,5,6,6,10,10,10);
        invoke(panel,"act",String.class,"deal");
        invoke(panel,"pointer",new Class<?>[]{float.class,float.class,boolean.class},new Object[]{575f,625f,true});
        check(game.invested()==40,"Blackjack Double mouse button failed");
        for(int frame=0;frame<200;frame++) panel.advance(1f/60);
        check(TokenBank.getTokens()==140 && game.paid()==80,"Dealer animation did not finish/pay");
        invoke(panel,"act",String.class,"bet:2");
        check(game.hands().isEmpty() && ((LabelAPI)get(panel,"result")).getText().isEmpty(),"Bet change retained stale result");
        BlackjackChecks.rig(game,10,6,2,10,4,10); invoke(panel,"act",String.class,"deal");
        Object[][] faces=(Object[][])get(panel,"faces");
        check((Boolean)get(faces[0][1],"hidden") && ((String)get(faces[0][1],"rankText")).isEmpty(),"Dealer hole card leaked");
        invoke(panel,"pointer",new Class<?>[]{float.class,float.class,boolean.class},new Object[]{265f,625f,false});
        invoke(panel,"pointer",new Class<?>[]{float.class,float.class,boolean.class},new Object[]{265f,625f,true});
        check(game.hands().get(0).cards().size()==3,"Blackjack Hit mouse button failed");
        invoke(panel,"pointer",new Class<?>[]{float.class,float.class,boolean.class},new Object[]{425f,625f,false});
        invoke(panel,"pointer",new Class<?>[]{float.class,float.class,boolean.class},new Object[]{425f,625f,true});
        for(int frame=0;frame<200;frame++) panel.advance(1f/60);
        check(TokenBank.getTokens()==142 && !(Boolean)get(faces[0][1],"hidden")
                && !((String)get(faces[0][1],"rankText")).isEmpty(),"Stand mouse button/dealer reveal failed");
    }

    @SuppressWarnings("unchecked")
    private static void pachinkoBatches() throws Exception {
        // Real buttons can launch 50, then 10 more while earlier balls are still falling.
        reset(); TokenBank.addTokens(2000);
        var panel=(gamblingden.pachinko.PachinkoPanel)machine(gamblingden.pachinko.PachinkoPanel.class);
        invoke(panel,"act",String.class,"category:TOKENS");
        invoke(panel,"pointer",new Class<?>[]{float.class,float.class,boolean.class},new Object[]{540f,623f,true});
        var live=(List<gamblingden.pachinko.PachinkoRound>)get(panel,"rounds");
        check(live.size()==50 && TokenBank.getTokens()==1900,"50-ball button cost/quantity mismatch");
        panel.advance(.2f);
        var swarm=(gamblingden.pachinko.PachinkoSwarm)get(panel,"swarm");
        check(swarm.launched()>1 && swarm.pending()>1,"Balls still run one at a time");
        invoke(panel,"pointer",new Class<?>[]{float.class,float.class,boolean.class},new Object[]{320f,623f,false});
        invoke(panel,"pointer",new Class<?>[]{float.class,float.class,boolean.class},new Object[]{320f,623f,true});
        check(live.size()==60 && TokenBank.getTokens()==1880,"Cannot add paid balls during a run");
        panel.processInput(List.of(key(Keyboard.KEY_SPACE)));
        check(live.size()==61 && TokenBank.getTokens()==1878,"Space still skips instead of adding a ball");
        invoke(panel,"act",String.class,"drop50");
        check(live.size()==61 && TokenBank.getTokens()==1878,"Over-cap batch partially charged");
        var all=new ArrayList<>(live);
        invoke(panel,"pointer",new Class<?>[]{float.class,float.class,boolean.class},new Object[]{725f,623f,false});
        invoke(panel,"pointer",new Class<?>[]{float.class,float.class,boolean.class},new Object[]{725f,623f,true});
        int winnings=all.stream().mapToInt(gamblingden.pachinko.PachinkoRound::getAwarded).sum();
        check(TokenBank.getTokens()==1878+winnings && ((gamblingden.pachinko.PachinkoWinnings)get(panel,"winnings")).tokens()==winnings
                && all.stream().allMatch(gamblingden.pachinko.PachinkoRound::isSettled),"Finish all lost/paid pending batch winnings");
        check(live.isEmpty() && swarm.balls().isEmpty(),"Completed physics bodies retained during continuous play");
        check(((LabelAPI)get(panel,"result")).getText().contains(winnings+" tokens")
                && ((LabelAPI)get(panel,"result")).getText().contains("61/61"),"Batch total not displayed accurately");
        check(swarm.getCollisions()>0,"UI does not use ball-to-ball collisions");
        int after=TokenBank.getTokens(); panel.finishOnDismissal(); panel.finishOnDismissal();
        check(TokenBank.getTokens()==after,"Batch double-paid on close");

        // Every exit settles every purchased ball, including balls still inside the launcher.
        for(String exit:List.of("escape","leave","external")) {
            reset(); TokenBank.addTokens(1000);
            var game=(gamblingden.pachinko.PachinkoPanel)machine(gamblingden.pachinko.PachinkoPanel.class);
            invoke(game,"act",String.class,"category:TOKENS"); invoke(game,"act",String.class,"drop50");
            var balls=new ArrayList<>((List<gamblingden.pachinko.PachinkoRound>)get(game,"rounds"));
            if(exit.equals("escape")) game.processInput(List.of(key(Keyboard.KEY_ESCAPE)));
            if(exit.equals("leave")) invoke(game,"act",String.class,"leave");
            if(exit.equals("external")) new gamblingden.pachinko.PachinkoDialogDelegate(game,()->{}).reportDismissed(0);
            game.finishOnDismissal();
            int paid=balls.stream().mapToInt(gamblingden.pachinko.PachinkoRound::getAwarded).sum();
            check(balls.stream().allMatch(gamblingden.pachinko.PachinkoRound::isSettled) && TokenBank.getTokens()==900+paid,"Lost queued ball on "+exit);
        }

        reset(); TokenBank.addTokens(99);
        var unaffordable=(gamblingden.pachinko.PachinkoPanel)machine(gamblingden.pachinko.PachinkoPanel.class);
        invoke(unaffordable,"act",String.class,"category:TOKENS"); invoke(unaffordable,"act",String.class,"drop50");
        check(TokenBank.getTokens()==99 && ((List<?>)get(unaffordable,"rounds")).isEmpty(),"Unaffordable batch spent some tokens");

        // Stock exhaustion and settlement order must match across frame rates and Finish all.
        for(var category:gamblingden.pachinko.PachinkoSettings.Category.values()) {
            String baseline=null;
            for(float dt:new float[]{0,1f/240,1f/60,1f/20,.5f}) {
                reset(); TokenBank.addTokens(5000);
                for(int i=0;i<18;i++) hullmods.add(hullmod("batch_"+i,1));
                weapons.add(weapon("batch_gun",1,false));
                var game=(gamblingden.pachinko.PachinkoPanel)machine(gamblingden.pachinko.PachinkoPanel.class);
                invoke(game,"act",String.class,"category:"+category.name());
                ((Random)get(game,"random")).setSeed(9187);
                invoke(game,"act",String.class,"drop50");
                var balls=new ArrayList<>((List<gamblingden.pachinko.PachinkoRound>)get(game,"rounds"));
                if(dt==0) invoke(game,"act",String.class,"skip");
                else for(int frame=0;frame<10000 && !((List<?>)get(game,"rounds")).isEmpty();frame++) game.advance(dt);
                check(balls.size()==50 && balls.stream().allMatch(gamblingden.pachinko.PachinkoRound::isSettled),"Not all category balls settled");
                int units=balls.stream().mapToInt(gamblingden.pachinko.PachinkoRound::getAwarded).sum();
                int refunds=balls.stream().mapToInt(gamblingden.pachinko.PachinkoRound::getRefunded).sum();
                check(chips.isEmpty() && guns.isEmpty() && TokenBank.getTokens()==5000-50*balls.get(0).offer.cost+refunds+(category.name().equals("TOKENS")?units:0),
                        "Shared board paid items early or delayed tokens/refunds");
                game.finishOnDismissal();
                check(TokenBank.getTokens()==5000-50*balls.get(0).offer.cost+refunds+(category.name().equals("TOKENS")?units:0),"Shared-category accounting mismatch");
                check(chips.size()==(category.name().equals("HULLMODS")?units:0) && guns.getOrDefault("batch_gun",0)==(category.name().equals("WEAPONS")?units:0)
                        && credits==0,"Wrong cargo from shared board");
                String state=balls.stream().map(b->b.board.getPocket()+":"+b.getResult()).toList().toString()+new java.util.TreeSet<>(chips)+TokenBank.getTokens();
                if(baseline==null) baseline=state;
                else check(state.equals(baseline),"FPS/skip changed scarce stock settlement order");
                game.finishOnDismissal();
            }
        }
    }

    private static org.json.JSONObject readJson(String path) throws Exception {
        return new org.json.JSONObject(String.join("\n",java.nio.file.Files.readAllLines(java.nio.file.Path.of(path))
                .stream().filter(line->!line.stripLeading().startsWith("#")).toList()));
    }
    private static FighterWingSpecAPI fighter(String id,float rarity,String... tags) {
        return proxy(FighterWingSpecAPI.class,(m,a)->switch(m.getName()) {
            case "getId","getWingName" -> id;
            case "getBaseValue" -> 1000f;
            case "getRarity" -> rarity;
            case "hasTag" -> Arrays.asList(tags).contains(a[0]);
            default -> null;
        });
    }
    private static void expandedRewards() throws Exception {
        reset();
        for(int i=0;i<8;i++) fighterSpecs.add(fighter("wing"+i,1));
        for(String tag:List.of(Tags.NO_DROP,Tags.RESTRICTED,"no_drop_salvage","no_sell","mission_item"))
            fighterSpecs.add(fighter(tag,1000,tag));
        fighterSpecs.add(fighter("zero",0)); fighterSpecs.add(fighter("nan",Float.NaN));
        for(int seed=0;seed<100;seed++) {
            wings.clear(); Payout crate=new Payout(); crate.add(Prize.FIGHTERS_LARGE,1);
            check(crate.getWeaponCount()==0 && crate.getFighterCount()==4,"Fighters mixed with weapons");
            var receipt=crate.grant(new Random(seed));
            check(wings.size()==4 && wings.values().stream().allMatch(n->n==1),"Fighter crate repeated a wing despite sufficient stock");
            check(wings.keySet().stream().allMatch(id->id.startsWith("wing")),"Invalid fighter LPC awarded");
            check(receipt.getFighters()==4 && receipt.describe().contains("4 fighter LPCs"),"Fighter receipt mismatch");
            crate.grant(new Random(0));check(wings.size()==4 && wings.values().stream().allMatch(n->n==1),"Fighter prize paid twice");
        }
        Payout sp=new Payout();sp.add(Prize.STORY_POINT,1);
        check(!sp.canDouble() && !sp.doubleUp(),"Story points can be doubled");
        check(sp.describe().equals("1 story point"),"Story-point preview mismatch");
        check(sp.grant(new Random(0)).getStoryPoints()==1 && storyPoints==1,"Story point not awarded");
        sp.grant(new Random(0));check(storyPoints==1,"Story point awarded twice");
        storyPoints=Integer.MAX_VALUE-1;Payout cap=new Payout();cap.add(Prize.STORY_POINT,5);
        check(cap.grant(new Random(0)).getStoryPoints()==1 && storyPoints==Integer.MAX_VALUE,"Story point overflow");
        for(int stake=0;stake<3;stake++) for(int spin=0;spin<1000;spin++)
            check(!SlotMachine.pull(5,stake,new Random(spin)).symbols.contains(Prize.STORY_POINT),"Story point below 8-token stakes");
        int stories=0;
        Random random=new Random(562);
        for(int spin=0;spin<100000;spin++) if(SlotMachine.pull(1,3,random).symbols.get(0)==Prize.STORY_POINT) stories++;
        check(stories>110 && stories<220,"Story-point rate is not rare or never pays: "+stories);
        float oldHit=Config.HIT_CHANCE[3];var oldWeights=Config.WEIGHTS[3];
        try {
            Config.HIT_CHANCE[3]=1;Config.WEIGHTS[3]=new HashMap<>(Map.of(Prize.STORY_POINT,1f));
            var result=SlotMachine.pull(3,3,new Random(1));
            check(!result.fullHouse && !result.payout.canDouble() && result.payout.describe().equals("3 story points"),
                    "All-story reels automatically doubled or lied about doubling");
        } finally { Config.HIT_CHANCE[3]=oldHit;Config.WEIGHTS[3]=oldWeights; }
        int[] expected={2000,6250,15000,30000};
        for(int i=0;i<4;i++) check(Config.creditsPaid(i)==expected[i],"Credits not cut to one quarter");
        var loader=lunalib.backend.ui.settings.LunaSettingsLoader.INSTANCE;
        boolean loaded=loader.getHasLoaded();var previous=lunalib.backend.ui.settings.LunaSettingsLoader.getSettings();
        var settings=new org.lazywizard.lazylib.JSONUtils.CommonDataJSONObject("unused-test-settings");
        try {
            loader.setHasLoaded(true);
            lunalib.backend.ui.settings.LunaSettingsLoader.setSettings(new HashMap<>(Map.of(Ids.MOD_ID,settings)));
            settings.put("gd_credits_max",120000);
            check(Config.creditsPaid(3)==30000,"Saved old Luna value defeated credit reduction");
            settings.put("gd_credit_percent",50);check(Config.creditsPaid(3)==60000,"Credit slider ignored");
            settings.put("gd_hit_max",Math.round(Config.HIT_CHANCE[3]*100));
            settings.put("gd_tokens_max",Config.TOKENS_PAID[3]);
            for(Prize prize:Prize.values()) if(prize.isCrate()) settings.put("gd_count_"+prize.id,prize.count);
            settings.put("gd_credit_percent",0);check(Config.creditsPaid(3)==0,"Zero-credit setting ignored");
            check(!SlotMachine.stripFor(3).contains(Prize.CREDITS),"Disabled cash still appears on the strip");
            for(int spin=0;spin<1000;spin++) check(!SlotMachine.pull(5,3,new Random(spin)).symbols.contains(Prize.CREDITS),
                    "Cash icon shown for a disabled credit reward");
        } finally { lunalib.backend.ui.settings.LunaSettingsLoader.setSettings(previous);loader.setHasLoaded(loaded); }
    }
    private static void jackpotDefaults() throws Exception {
        var json=readJson(gamblingden.jackpot.JackpotGame.CONFIG_PATH);
        var rows=json.getJSONArray("rewards");specialSpecs.clear();
        for(int i=0;i<rows.length();i++) {
            var row=rows.getJSONObject(i);String id=row.getString("id");
            if(row.optString("kind","special").equals("special")) specialSpecs.put(id,proxy(SpecialItemSpecAPI.class,(m,a)->switch(m.getName()) {
                case "getId" -> id;
                case "getName" -> id.replace('_',' ');
                case "getIconName" -> "graphics/icons/cargo/"+id+".png";
                default -> null;
            }));
        }
        gamblingden.jackpot.JackpotGame.clearCache();
        check(gamblingden.jackpot.JackpotGame.pool(8).size()==17,"Default jackpot list missing items");
    }
    private static void jackpot() throws Exception {
        reset();jackpotDefaults();
        for(int stake:new int[]{2,4,8}) {
            var pool=gamblingden.jackpot.JackpotGame.pool(stake);
            check(pool.stream().anyMatch(r->r.id().equals("gamma_core")),"Gamma core missing");
            check(pool.stream().anyMatch(r->r.id().equals("beta_core"))==(stake>=4),"Beta stake gate");
            check(pool.stream().anyMatch(r->r.id().equals("alpha_core"))==(stake>=8),"Alpha stake gate");
            check(pool.stream().allMatch(r->r.minStake()<=stake),"Locked item in jackpot pool");
            saved.put(Ids.KEY_TOKENS,10000000);
            int wins=0,draws=100000;Random random=new Random(stake);
            for(int spin=0;spin<draws;spin++) {
                var round=gamblingden.jackpot.JackpotGame.buy(stake,random);
                check(round!=null && round.symbols.size()==3,"Valid jackpot pull failed");
                var first=round.symbols.get(0);
                boolean match=first!=null && round.symbols.stream().allMatch(r->r!=null && r.id().equals(first.id()));
                check((round.winner()!=null)==match,"Nonmatching jackpot won");
                int before=specialItems.values().stream().mapToInt(n->n).sum()+commodities.values().stream().mapToInt(n->n).sum();
                String result=round.finish();round.finish();
                int after=specialItems.values().stream().mapToInt(n->n).sum()+commodities.values().stream().mapToInt(n->n).sum();
                check(after-before==(match?1:0),"Jackpot paid more than one item or paid on a mismatch");
                if(match) { wins++;check(result.equals("Collected: 1 "+first.name()+"."),"Jackpot receipt differs from symbol"); }
            }
            double expected=gamblingden.jackpot.JackpotGame.matchChance(stake);
            check(Math.abs((double)wins/draws-expected)<.001,"Independent match odds differ from display");
            check(TokenBank.getTokens()==10000000-draws*stake*3,"Jackpot charged wrong cost");
            System.out.printf("Relic Jackpot: %d per reel, %.3f%% expected, %.3f%% observed%n",stake,100*expected,100d*wins/draws);
        }
        saved.put(Ids.KEY_TOKENS,5);
        check(gamblingden.jackpot.JackpotGame.buy(2,new Random())==null && TokenBank.getTokens()==5,"Unaffordable jackpot charged");
        specialSpecs.put("mission_reward",proxy(SpecialItemSpecAPI.class,(m,a)->m.getName().equals("hasTag") && a[0].equals("mission_item")));
        var custom=new org.json.JSONObject("{'symbolChance':1,'rewards':[{'id':'missing'},{'id':'soil_nanites'},"
                +"{'id':'mission_reward'},{'id':'soil_nanites'},{'kind':'commodity','id':'alpha_core','minStake':2},{'kind':'commodity','id':'fuel'}]}");
        Method load=gamblingden.jackpot.JackpotGame.class.getDeclaredMethod("load",org.json.JSONObject.class);load.setAccessible(true);load.invoke(null,custom);
        check(gamblingden.jackpot.JackpotGame.pool(2).size()==1 && gamblingden.jackpot.JackpotGame.pool(8).size()==2,
                "Missing/duplicate/ordinary item or unlocked alpha entered custom list");
        for(String close:List.of("skip","leave","escape","external","animated")) {
            reset();recordIcons=true;TokenBank.addTokens(100);
            var panel=(gamblingden.jackpot.JackpotPanel)machine(gamblingden.jackpot.JackpotPanel.class);
            invoke(panel,"pointer",new Class<?>[]{float.class,float.class,boolean.class},new Object[]{320f,620f,true});
            check(TokenBank.getTokens()==94 && (Boolean)get(panel,"spinning"),"Jackpot mouse Pull failed");
            invoke(panel,"act",String.class,"stake:8");check((Integer)get(panel,"stake")==2,"Jackpot stake changed mid-spin");
            invoke(panel,"pointer",new Class<?>[]{float.class,float.class,boolean.class},new Object[]{320f,620f,true});
            check(TokenBank.getTokens()==94,"Held mouse bought two pulls");
            if(close.equals("skip")) {
                invoke(panel,"pointer",new Class<?>[]{float.class,float.class,boolean.class},new Object[]{510f,620f,false});
                invoke(panel,"pointer",new Class<?>[]{float.class,float.class,boolean.class},new Object[]{510f,620f,true});
            } else if(close.equals("leave")) {
                invoke(panel,"pointer",new Class<?>[]{float.class,float.class,boolean.class},new Object[]{700f,620f,false});
                invoke(panel,"pointer",new Class<?>[]{float.class,float.class,boolean.class},new Object[]{700f,620f,true});
            } else if(close.equals("escape")) panel.processInput(List.of(key(Keyboard.KEY_ESCAPE)));
            else if(close.equals("external")) {
                int[] callbacks={0};var delegate=new gamblingden.jackpot.JackpotDialogDelegate(panel,()->callbacks[0]++);
                delegate.reportDismissed(0);delegate.reportDismissed(0);check(callbacks[0]==1,"Jackpot close callback repeated");
            } else for(int frame=0;frame<300;frame++) panel.advance(1f/60);
            check(!(Boolean)get(panel,"spinning") && specialItems.get("soil_nanites")==1,"Exit/skip failed to collect one match: "+close);
            check(((LabelAPI)get(panel,"result")).getText().equals("Collected: 1 soil nanites."),"Jackpot screen receipt mismatch");
            Pbuffer buffer=new Pbuffer(1200,800,new PixelFormat(),null);
            try {
                buffer.makeCurrent();drawnIcons.clear();panel.renderBelow(1);
                check(drawnIcons.size()==3 && drawnIcons.stream().allMatch(icon->icon.path().equals("graphics/icons/cargo/soil_nanites.png")),
                        "Jackpot icons differ from actual collected item");
            } finally { buffer.destroy(); }
            if(close.equals("skip") || close.equals("animated")) {
                invoke(panel,"act",String.class,"stake:4");
                check(((LabelAPI)get(panel,"result")).getText().isEmpty() && get(panel,"round")==null,"New jackpot stake retains stale prize");
                int checked=0;
                for(Object b:(List<?>)get(panel,"buttons")) if((Boolean)get(b,"checked")) checked++;
                check(checked==1,"Jackpot stakes not exclusive");
            }
            panel.finishOnDismissal();check(specialItems.get("soil_nanites")==1,"Jackpot exit paid twice");
        }
        load.invoke(null,new org.json.JSONObject("{'rewards':[]}"));
        check(gamblingden.jackpot.JackpotGame.buy(2,new Random())==null,"Empty jackpot pool accepted payment");
        jackpotDefaults();recordIcons=false;
    }

    public static void main(String[] args) throws Exception {
        if(args.length>0 && args[0].equals("fast-renderer")) {
            setup(); FastRendererChecks.run(RegressionChecks::machine); return;
        }
        setup(); reels(); prizes(); ships(); ui(); rewardDisplay(); legacy(); stakes(); odds();
        PachinkoPhysicsChecks.run(); PachinkoPhysicsChecks.multiBall(); pachinko(); pachinkoSettings(); pachinkoBatches();
        BlackjackChecks.run(); blackjackUI(); expandedRewards(); jackpot();
        FastRendererChecks.run(RegressionChecks::machine);
        System.out.println("PASS: "+assertions+" checks, including 4,500 reel completions; mock campaign and offscreen graphics only.");
    }
}
