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

**Set the machine up.** One reel or five, low stakes or high. Each reel costs its own stake, so
the pull costs what you tell it to cost. More reels is more chances at once; higher stakes puts
better prizes on the strip, not better odds.

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

## Credits

The cabinet owes its shape to the Tachy-Impact machine in **Interastral Peace Casino** by Emanon6
and WolframSegler, which is shared for free non-commercial use. Go and play it; it does poker and
blackjack against a real opponent, which this mod deliberately does not.
