# Gambling Den

A back-room gambling den at every port of size 6 or more. Sell surplus hulls for tokens,
then play Slots or Pachinko for hullmod blueprints, weapons, tokens, and more.

Gathering hull mods from salvage is slow. This gives you something else to do with the pile of
recovered frigates you are never going to fly.

Gambling Den is the venue, not the name of a machine. **Slots** and **Pachinko** share the den
and token balance, with their own rules and settings. Card games are planned for later updates.

## How it works

**Find it.** *Visit the gambling den* is its own option when you dock at any port of size 6 or
more — not a bar event, so it is always there rather than turning up at random. Cabinets behind
a curtain that used to be a thermal blanket, watched by a keeper. The size
requirement is the `$marketSize >= 6` line in `data/campaign/rules.csv` if you want it elsewhere.

**Pay in hulls, not credits.** The keeper buys ships off you for tokens, priced on how much ship
it is rather than what it is worth on paper, so a pile of frigates is worth having. Every d-mod
knocks a slice off. Your flagship is not for sale. Blueprint chips you have already read are
worth tokens too.

Selected ships get a named, itemised quote and a confirmation before sale. Removable weapons
and fighter wings, including those on ship modules, return to your cargo.

## Slots

**Set the machine up.** One reel or five, with stakes of **1, 2, 4 or 8 tokens per reel**. Each reel costs its own stake, so
the pull costs what you tell it to cost. More reels is more chances at once; higher stakes puts
larger prizes on the strip and raises the chance that an individual reel pays.

**Pull.** Every reel is rolled and paid on its own — nothing has to line up. Most reels bust,
the way a slot machine is supposed to; the ones that do not are worth having. Match every reel
and the whole payout doubles.

**What comes out.** Credits, more tokens so you can keep going, weapon crates in three sizes, and
hull mod boxes in three sizes — a large box is ten blueprints handed over at once.

**It never gives you a duplicate.** Boxes skip anything you already know and anything you are
already carrying a chip for, including within the same box. If the machine has run out of hull
mods you do not know, a box settles up in cash instead.

**Double or nothing.** Win anything and you can risk the whole payout on one more roll. Slightly
worse than even odds, because of course it is.

Doubling stops before a prize can exceed 1 billion credits/tokens or 10,000 items. Closing the
machine settles a paid spin and collects its winnings. The collection message reports what
actually entered cargo, including credits substituted for unavailable hullmods.

Every action has a button. Space pulls and Escape leaves, but neither is the only way to do it.

## Pachinko

Choose **Hullmods**, **Weapons**, or **Tokens**, then buy one ball with the Drop ball button.
One ball runs at a time. Default prices are 4 tokens for a hullmod ball, 2 for a weapon ball,
and 2 for a token ball. The ball falls from the centre and bounces through ten rows of pegs.

The pocket amounts, from left to right, are:

`16 | 8 | 4 | 2 | 1 | 0 | 1 | 2 | 4 | 8 | 16`

These are direct quantities. An 8 on the hullmod board gives eight random blueprint chips,
not eight boxes and not a second roll for a prize. The centre pays nothing; the outer pockets
are the rare large wins. Rewards enter cargo or the token balance when the ball lands.

The visible ball's actual landing pocket decides the reward. The simulation uses fixed time
steps, so frame rate does not change the result. **Finish drop** advances the same ball to its
landing. Leave, Escape, or an external dialog closure also finish and pay a purchased ball once.
An exceptional stuck ball refunds its price instead of inventing a landing.

Before a hullmod bet, pocket amounts are capped to the number of eligible blueprints left.
The board shows the reduced amounts and a shortage notice. It never substitutes credits.
An exhausted category cannot accept a bet. If another mod makes the promised reward unavailable
during a drop, that ball is refunded. If settings or availability change after the displayed
quote, the next Drop click refreshes the board without charging; click again to accept it.

Category choices are mutually exclusive and stay locked during a drop. All actions are
clickable. Space drops or finishes a ball; Escape leaves. There are no tutorial panels.

## Tuning

**LunaLib settings page**, adjustable with sliders while the game is running: how many blueprints
are in each size of hull mod box, how many weapons in each size of crate (1 to 100 either way),
what the cash and token symbols pay at each stake, the chance a reel pays anything at all, and
what the keeper gives per fleet point of hull.

The **Pachinko** tab has separate ball-price sliders for each category and six pocket-amount
sliders (0–100). Each amount applies symmetrically at the same distance from the centre.
They do not use the Slots crate-size sliders. A purchased ball keeps its quoted price and
pocket amounts if settings change during its fall.

**`data/config/gambling_den.json`** holds the deeper wiring — reel costs, the reel strip weights
for all four stakes, the payout variance and the double-or-nothing chance. Edit and reload a
save; no rebuild.

## Building

Requires LunaLib installed (it is found automatically under `mods/`). Set `starsectorPath` in
`gradle.properties` to your Starsector install, then:

```
./gradlew jar
```

The jar lands in `jars/`.

Run `./gradlew check` for the standalone regression checks. They use mock campaign data and
offscreen graphics; they do not open or change a live game. The graphics checks require the
game's Windows native libraries. Run `./gradlew releaseZip` to check, build and package the mod
with both icon sizes into `GamblingDen.zip`.

If `fr.jar` is installed in `starsector-core`, the checks also send 1,200 panel frames through
its actual graphics bridge and verify that the old crash condition is detected. This uses
an offscreen context, not a running campaign.

## Changes in 1.1.0

Adds Pachinko as a separate game in the den. Includes targeted categories, the 16–0–16 pocket
layout, physical ball bounces, direct item payouts, mouse controls, and LunaLib settings.
Slots rules and odds are unchanged from the playtested 1.0.0 release.

Checks cover 10,000 physical ball drops, frame-rate and skip consistency, all pockets in all
three reward categories, mouse hitboxes, safe dismissal, exact cargo transfers, settings
changes during a drop, 100-item rewards, exhausted pools, and the Fast Rendering bridge.
Pachinko has not yet been playtested in a live campaign.

## Release 1.0.0

The first stable release, confirmed working through the user's in-game playtest. Gameplay,
settings and rewards are unchanged from 0.5.0. This is the tested baseline for adding more
games to the den; Slots remains its first game.

## Changes in 0.5.0

Slots has four stake levels at 1, 2, 4 and 8 tokens per reel. The buttons show those costs.
The fourth level, Max, defaults to a 32% hit chance and cash/token prizes of 120,000 credits
or 90 tokens before variance. Hullmod boxes make up 40% of its winning symbols. Its hit chance
and cash/token payouts have their own LunaLib sliders; the other three levels retain their
previous reward settings. The cabinet is titled Slots, and the den menu offers Play slots.

Includes the display fixes below and the Fast Rendering crash fix.

## Changes in 0.4.4

Changing the stakes or reel count now clears the previous reward text, reel labels and win
effects. Clicking the current setting leaves the result untouched. Stopped reels outline the
middle row and dim the neighbouring symbols. No odds or rewards have changed.

Regression checks now compare the drawn middle-row icons, labels, reward text and cargo
for every prize type, one to five reels, and normal and skipped spins.

## Changes in 0.4.3

Fixes the fatal "Asynchronous pipeline stall" introduced in 0.4.2 when using Fast Rendering.
Reel clipping no longer reads graphics state each frame. The panel still restores the
surrounding UI's clipping state after drawing. Rewards, odds and ship prices are unchanged.

## Changes in 0.4.2

Hullmod boxes now make up 12%, 24% and 32% of winning symbols at low, mid and high stakes.
The overall per-reel win chances remain 16%, 20% and 26%. At low stakes this is roughly one
hullmod box per 18 three-reel pulls, up from one per 35.

This update fixes ship equipment loss, ambiguous sale quotes, clipping of the UI, lost prizes
when closing a spin, incorrect collection messages and payout overflow. Fighter-only,
restricted, salvage-excluded and zero-rarity weapons are excluded from crates; zero-rarity
hullmods are excluded too. Crate contents are fixed when a spin starts, so changing a slider
cannot change an existing prize. Compatibility classes allow old bar events to be read and
removed when loading earlier saves; no new bar events are created.

## Credits

The cabinet owes its shape to the Tachy-Impact machine in **Interastral Peace Casino** by Emanon6
and WolframSegler, which is shared for free non-commercial use. Its blackjack and poker sources
have been inspected as references for future card games. No card-game code or assets are
included in this release; Pachinko is an original implementation.
