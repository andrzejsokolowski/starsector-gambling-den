# Gambling Den

A back-room gambling den at every port of size 6 or more. Sell surplus hulls for tokens,
then play Slots, Pachinko, Blackjack, or Relic Jackpot.

Gathering hull mods from salvage is slow. This gives you something else to do with the pile of
recovered frigates you are never going to fly.

Gambling Den is the venue, not the name of a machine. All four games share the den
and token balance, with their own rules. Poker is planned for a later update.

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
across two or more reels and the whole payout doubles, unless it contains story points.

Prizes include credits, tokens, weapon crates, fighter crates, and hullmod boxes.
A large hullmod box holds ten blueprints by default. Fighter crates hold 1, 2, or 4 fighter LPCs,
items that equip a carrier with fighter wings. Each LPC gets its own weighted random draw.
Fighter rewards avoid repeats until every eligible wing appears. Built-in, restricted, mission,
no-drop, and no-sell wings are excluded.

At 8 tokens per reel, a rare symbol pays one story point. Its default chance is about 0.16% per reel.
A payout that contains story points cannot be doubled, automatically or with Double or nothing.

**It never gives you a duplicate.** Boxes skip anything you already know and anything you are
already carrying a chip for, including within the same box. If the machine has run out of hull
mods you do not know, a box settles up in cash instead.

**Double or nothing.** Win a prize without story points and you can risk the whole payout on one more roll. Slightly
worse than even odds, because of course it is.

Doubling stops before a prize can exceed 1 billion credits/tokens or 10,000 items. Closing the
machine settles a paid spin and collects its winnings. The collection message reports what
actually entered cargo, including credits substituted for unavailable hullmods.

Every action has a button. Space pulls and Escape leaves, but neither is the only way to do it.

## Pachinko

Choose **Hullmods**, **Weapons**, or **Tokens**, then drop **1, 10 or 50 balls**. Keep buying
balls while earlier ones are falling. Each button shows the full cost of its batch; purchases
are all-or-nothing. Default prices per ball are 4 tokens for hullmods, 2 for weapons, and 2
for tokens. Balls feed rapidly from the centre, bouncing off ten rows of pegs and each other.
The board allows up to 100 falling or queued balls at once. Landed balls free room for more.

The pocket amounts, from left to right, are:

`16 | 8 | 4 | 2 | 1 | 0 | 1 | 2 | 4 | 8 | 16`

These are direct quantities. An 8 on the hullmod board gives eight random blueprint chips,
not eight boxes and not a second roll for a prize. The centre pays nothing; the outer pockets
are the rare large wins. Each landing adds item rewards to the pending winnings total.
The game generates and transfers items when you leave the Pachinko screen.
Pending items remain across batches and category changes. Token wins and refunds pay on landing,
so you can immediately use them to buy more balls.

Each weapon gets a separate random draw. Within one ball's reward, a weapon cannot repeat
until every eligible weapon type appears. A reward larger than the eligible pool starts another
cycle through that pool. Different balls can still give the same weapon.

Each visible ball's actual landing pocket decides its reward. Ball-to-ball collisions can
change its path and result. All balls share one fixed-step simulation, so frame rate does not
change the result for the same launches. Finish all advances that same shared simulation without collecting rewards.
Leave & collect, Escape, and an external dialog closure finish every purchased ball and collect the winnings once.
This includes queued balls. An exceptional stuck ball refunds its price.
The result line totals the rewards and refunds for the run.
The To collect line shows only the pending items for the whole visit.

Before a hullmod run, pocket amounts are capped to the number of eligible blueprints left.
During a run, the displayed board stays fixed and balls share the remaining stock.
Pending blueprint wins reserve stock, so later balls cannot claim it again. A ball
whose prize cannot be filled is refunded in full; once stock is empty, remaining balls refund
their cost even if they land in zero. This is stated before buying. There are no duplicate
blueprints or credit substitutions. An exhausted category cannot accept new bets.
At collection, the game checks current stock again. If another mod removed needed stock,
the game refunds each affected winning ball instead of giving a partial prize.

Live setting edits apply after the board clears, including for extra balls bought during a
run: they use the same price and pocket amounts still shown on the board. If settings or
availability differ from the quote before a new run, the next purchase click refreshes the
board without charging; click again to accept it.

Category choices are mutually exclusive and stay locked while balls remain. All actions are
clickable. Space adds one ball, including during a run; Escape leaves. There are no tutorial panels.

## Blackjack

Play blackjack opens a separate card table in the den. Choose an even bet from 2 to 1,000 tokens,
then press Deal. Hit, Stand, Double, Split, and Leave each have a mouse button.
Bet buttons and the -2, +2, and x2 controls work between rounds.

The table uses six shuffled decks and reshuffles between rounds when fewer than 52 cards remain.
The dealer stands on soft 17, a hand with an ace counted as 11. An opening blackjack pays 3:2
profit, an ordinary win pays 1:1, and a push returns the bet. Even bets keep these payouts exact
in whole tokens. The dealer checks for an opening blackjack before you can place extra bets.

Double takes one additional bet, draws one card, and ends that hand. Split accepts two cards of
the same rank and takes one additional bet. You can split once and double after a split.
Split aces receive one additional card each and stand. A split hand with 21 pays as an ordinary win.
This table has no insurance, surrender, loans, or credit bets.

Winnings enter the token balance when the round ends. During a round, the exit button reads
Stand & leave. That button, Escape, and an external closure stand on unfinished hands and resolve
the dealer once. Leaving cannot cancel a paid bet or collect it twice.

## Relic Jackpot

This separate machine always has three reels. Stakes are 2, 4, or 8 tokens per reel,
so a pull costs 6, 12, or 24 tokens. Three copies of the same item on the middle row
award exactly one item. Blanks and mismatches pay nothing. There are no refunds, pity wins,
consolation credits, or doubling. Closing or skipping finishes the paid spin and collects its result once.

Gamma cores enter at 2 tokens per reel, beta cores at 4, and alpha cores at 8.
Higher stakes keep earlier rewards and add more colony items.
The default list contains fourteen colony items and three AI core grades.
Mission items are not included. Each reel rolls independently.
The default chance of any match is about 1.71% / 0.43% / 0.23%.
Higher stakes unlock rarer items, not a higher win rate. The screen shows the current match chance.

Edit `data/config/jackpot_rewards.json` to change the dedicated reward list, item weights, minimum stakes, and blank chance.
Use special-item IDs from the installed game or mods. Optional `data` identifies a parameterized special item.
Missing, mission, restricted, and no-drop items are skipped. Only AI cores can use the commodity entry type.
The core stake gates still apply if the list gives a lower minimum.
Reload a save after edits. Empty lists disable purchases.

## Tuning

The Credit payout percentage slider defaults to 25%. It applies to the base credit amounts,
including values saved before this update, and cash for unavailable blueprints.
Default credit symbols now pay 2,000 / 6,250 / 15,000 / 30,000 before random variance.
Unavailable blueprints pay 5,000 credits each by default. Already-won prizes keep their value.
Fighter crate sizes have their own sliders, from 1 to 100 LPCs.

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

If `fr.jar` is installed in `starsector-core`, the checks also send 2,400 panel frames through
its actual graphics bridge and verify that the old crash condition is detected. This uses
an offscreen context, not a running campaign.

## Changes in 1.3.0

Blackjack ranks use larger vector strokes without the shadow from the small interface font.
The user confirmed that blackjack gameplay works. Card rules and payouts do not change.

Slots add fighter crates and rare story points at 8-token stakes.
Story-point payouts cannot be doubled. Fighter crate sizes have LunaLib sliders.
Credit symbols and cash for unavailable blueprints now pay one quarter of their old amounts by default.
The new percentage slider also applies to previously saved credit values.

Relic Jackpot adds a separate three-match machine with a dedicated list of colony items and AI cores.
It pays one item per match, with no consolation payouts or doubling.
The token-to-ship converter remains a proposal and is not included.

Tests cover 300,000 jackpot pulls, exact icon-to-reward agreement, cargo awards, mouse buttons, safe exits, fighter variety, and story-point gates.
The existing blackjack and Pachinko checks still pass. All four games pass the Fast Rendering test.
The new visuals and rewards still need an in-game test.

## Changes in 1.2.0

Adds Blackjack with token bets, a six-deck table, 3:2 blackjack payouts, splitting, and doubling.
The model adapts Interastral Peace Casino code under its non-commercial reuse terms.
The card drawings and interface are new. The release includes the original code notice and credits
in `THIRD_PARTY_NOTICES.txt`. Interastral Peace Casino does not need to be installed.

Pachinko token wins and refunds now pay on landing. Only items wait for collection on exit.
Weapon rewards now draw without repeats within each ball until the eligible pool runs out.
The user confirmed that deferred item collection works in game. Slots rules and odds are unchanged.

Tests cover 20,000 blackjack rounds, fixed card sequences, split hands, dealer blackjacks, mouse
buttons, safe exits, and Fast Rendering. Weapon tests cover distinct four-weapon rewards,
small pools, and varied draws. Blackjack and the latest Pachinko changes still need an in-game test.

## Changes in 1.1.2

Pachinko holds winnings until you leave the screen. Landings no longer scan item pools, generate
items, or change cargo. The To collect line shows all pending winnings across batches and categories.
Finish all finishes the balls without collecting. Leave & collect awards the combined haul once,
including token wins and refunds. Weapon rewards of the same type enter cargo together.

Tests cover mixed rewards, 20,000 pending weapons, stock reservations, refunds, category changes,
and collection through every exit. They also reject item generation and cargo access during play.
The Fast Rendering compatibility tests pass. The user confirmed that deferred collection works in game.
Slots rules, ball prices, pocket amounts, and ball physics are unchanged.

## Changes in 1.1.1

Adds continuous multi-ball Pachinko, 1/10/50-ball buttons, a rapid central launcher, and real
ball-to-ball collisions. Up to 100 balls can be queued or falling at once. The board displays
live ball counts and combined winnings. Finish all and every exit settle the entire paid run.
Slots, ball prices and pocket amounts are unchanged.

Checks include 5,000 colliding balls, 50/100-ball frame-rate comparisons, all reward categories,
scarce stock, live setting edits, 6,000-item batch payouts, real mouse hitboxes, queued-ball
settlement on every exit, and a 100-ball board through Fast Rendering. The user tested multi-ball
play and reported a pause when balls landed. Version 1.1.2 moves item generation to exit.

## Changes in 1.1.0

Adds Pachinko as a separate game in the den. Includes targeted categories, the 16–0–16 pocket
layout, physical ball bounces, direct item payouts, mouse controls, and LunaLib settings.
Slots rules and odds are unchanged from the playtested 1.0.0 release.

Checks cover 10,000 physical ball drops, frame-rate and skip consistency, all pockets in all
three reward categories, mouse hitboxes, safe dismissal, exact cargo transfers, settings
changes during a drop, 100-item rewards, exhausted pools, and the Fast Rendering bridge.
The user confirmed that the single-ball Pachinko game worked in a live campaign.

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
and WolframSegler, which is shared for free non-commercial use. Blackjack adapts its rules model
with token accounting and corrections for split hands and dealer blackjacks.
`THIRD_PARTY_NOTICES.txt` preserves the applicable notice. No Interastral card art, music, or logos
are included. Pachinko, the blackjack interface, and its card drawings are original implementations.
