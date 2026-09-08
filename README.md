# Hullmod Dispenser

A rigged machine in the back of a bar that pays out in hull mod blueprints, and takes your
surplus hulls as payment.

Gathering hull mods from salvage is slow. This gives you something else to do with the pile of
recovered frigates you are never going to fly.

## How it works

**Find it.** A bar event at independent ports. A scuffed cabinet in the alcove past the heads,
and a keeper who owns exactly one thing.

**Pay in hulls, not credits.** The keeper buys ships off you for tokens, priced on how much ship
it is rather than what it is worth on paper, so a pile of frigates is worth having. Every d-mod
knocks a slice off. Your flagship is not for sale.

**Pull the handle.** Three reels of hull mod icons. Three of a kind pays a blueprint chip of
matching quality. Two of a kind coughs up a few credits. The big pull costs more and always pays.

**It never gives you a duplicate.** The machine skips anything you already know and anything you
are already carrying a chip for. If you have learned everything it could offer, it says so
instead of wasting your tokens.

**Blueprint chips you have already read are worth tokens.** Hand them to the keeper.

**Mercy rules.** A run of bad luck is guaranteed to break: the machine owes you a payout after a
set number of empty pulls, and owes you a top-quality one after a longer run without one.

**Double or nothing.** Win a blueprint and you can risk it for a better one. Slightly worse than
even odds, because of course it is.

## Tuning

Every number lives in `data/config/hullmod_dispenser.json` and can be edited without rebuilding
the mod: what a pull costs, what a hull is worth, the odds for every outcome, the mercy
thresholds, and the double-or-nothing chance.

## Building

Set `starsectorPath` in `gradle.properties` to your Starsector install, then:

```
./gradlew jar
```

The jar lands in `jars/`.

## Credits

The slot cabinet owes its shape to the Tachy-Impact machine in **Interastral Peace Casino** by
Emanon6 and WolframSegler, which is shared for free non-commercial use. Go and play it; it does
poker and blackjack properly, which this mod deliberately does not.
