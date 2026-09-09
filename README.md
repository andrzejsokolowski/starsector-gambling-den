# Gambling Den

A back-room gambling den at every port of size 6 or more. It takes surplus hulls as payment and
pays out in whatever the reels feel like — cash, tokens, weapon crates, and boxes of hull mod
blueprints.

Gathering hull mods from salvage is slow. This gives you something else to do with the pile of
recovered frigates you are never going to fly.

## How it works

**Find it.** *Visit the gambling den* is its own option when you dock at any port of size 6 or
more — not a bar event, so it is always there rather than turning up at random. A machine behind
a curtain that used to be a thermal blanket, and a keeper who owns exactly one thing. The size
requirement is the `$marketSize >= 6` line in `data/campaign/rules.csv` if you want it elsewhere.

**Pay in hulls, not credits.** The keeper buys ships off you for tokens, priced on how much ship
it is rather than what it is worth on paper, so a pile of frigates is worth having. Every d-mod
knocks a slice off. Your flagship is not for sale. Blueprint chips you have already read are
worth tokens too.

Selected ships get a named, itemised quote and a confirmation before sale. Removable weapons
and fighter wings, including those on ship modules, return to your cargo.

**Set the machine up.** One reel or five, low stakes or high. Each reel costs its own stake, so
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

## Tuning

**LunaLib settings page**, adjustable with sliders while the game is running: how many blueprints
are in each size of hull mod box, how many weapons in each size of crate (1 to 100 either way),
what the cash and token symbols pay at each stake, the chance a reel pays anything at all, and
what the keeper gives per fleet point of hull.

**`data/config/gambling_den.json`** holds the deeper wiring — reel costs, the reel strip weights
for all three stakes, the payout variance and the double-or-nothing chance. Edit and reload a
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
and WolframSegler, which is shared for free non-commercial use. Go and play it; it does poker and
blackjack against a real opponent, which this mod deliberately does not.
