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
        WeaponPool.clearCache();
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
                List<CargoStackAPI> stacks = new ArrayList<>();
                for(String id:chips) stacks.add(proxy(CargoStackAPI.class,(sm,sa)->switch(sm.getName()) {
                    case "getSpecialDataIfSpecial" -> new SpecialItemData(Ids.MODSPEC,id);
                    case "getSize" -> 1f;
                    default -> null;
                }));
                yield stacks;
            }
            case "addSpecial" -> { check(chips.add(((SpecialItemData)a[0]).getData()),"Duplicate blueprint awarded"); yield null; }
            case "addWeapons" -> { guns.merge((String)a[0],(Integer)a[1],Integer::sum); yield null; }
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
            case "getPersistentData" -> saved;
            case "getPlayerFleet" -> player;
            case "getPlayerFaction" -> faction;
            case "getMemoryWithoutUpdate" -> mem;
            default -> null;
        }));
        Global.setSettings(proxy(SettingsAPI.class,(m,a)->switch(m.getName()) {
            case "getAllHullModSpecs" -> new ArrayList<>(hullmods);
            case "getAllWeaponSpecs" -> new ArrayList<>(weapons);
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
            case "getRarity" -> rarity;
            case "hasTag" -> Arrays.asList(tags).contains(a[0]);
            case "getBaseValue" -> 2000f;
            default -> null;
        });
    }
    private static WeaponSpecAPI weapon(String id, float rarity, boolean system, String... tags) {
        return proxy(WeaponSpecAPI.class,(m,a)->switch(m.getName()) {
            case "getWeaponId", "getWeaponName" -> id;
            case "getRarity" -> rarity;
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
        check(box.describe().contains("2 hull mod blueprints") && box.describe().contains("160,000 credits"),"Partial exhaustion preview");
        var receipt=box.grant(new Random(1));
        check(receipt.getBlueprints()==2 && receipt.getCredits()==160000,"Partial exhaustion receipt");
        check(credits==160000 && chips.size()==3,"Actual cargo differs from receipt");
        box.grant(new Random(1)); check(credits==160000,"Prize paid twice");
        check(BlueprintPool.isExhausted(),"Zero rarity/no-drop/known/held exclusion");
        Payout emptyBox=new Payout(); emptyBox.add(Prize.BOX_SMALL,1);
        check(emptyBox.describe().contains("60,000 credits"),"Exhausted box preview");
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
    public static void main(String[] args) throws Exception {
        if(args.length>0 && args[0].equals("fast-renderer")) {
            setup(); FastRendererChecks.run(RegressionChecks::machine); return;
        }
        setup(); reels(); prizes(); ships(); ui(); rewardDisplay(); legacy(); stakes(); odds();
        FastRendererChecks.run(RegressionChecks::machine);
        System.out.println("PASS: "+assertions+" checks, including 4,500 reel completions; mock campaign and offscreen graphics only.");
    }
}
