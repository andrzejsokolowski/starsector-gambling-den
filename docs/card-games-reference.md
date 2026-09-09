# Interastral card-game reference

Inspected on 2026-09-09, at the user's request, while adding Pachinko. Blackjack is a later
addition, not part of 1.1.0. The user explicitly welcomes adapting Interastral's implementation.

Source installed at `D:/Games/StarSector/mods/Interastral Peace Casino-1.6.3/`.
`mod_info.json` names Emanon6 and WolframSegler as authors.

## Reuse terms found in the installed mod

`Licences.txt` allows use, modification and redistribution of the rest of the mod for
non-commercial purposes. Preserve its notice and credit the authors when importing code.
This is not an unrestricted/permissive licence for commercial use.

Card art has separate LGPL-2.1 terms: SVG-cards by David Bellot, forked by htdebeer.
Music and logo have separate miHoYo notices. Do not casually copy those assets alongside code.
No Interastral code, cards, music or logo was imported for Pachinko.

## Useful code boundaries

- `src/data/scripts/casino/cards/blackjack/BlackjackGame.java`: model, hit, stand, double,
  one split, hand values and settlement. It is separate from the campaign UI.
- `cards/Deck.java`, `Card.java`, `Rank.java`, `Suit.java`, `GameType.java`: shared cards.
  Blackjack uses a six-deck shoe; poker uses one deck.
- `cards/blackjack/BlackjackPanelUI.java` and `BlackjackDialogDelegate.java`: card animation,
  controls and game-to-UI callbacks. The panel relies on `shared/BaseCardGamePanelUI.java`
  and shared rendering helpers.
- `interaction/BlackjackHandler.java`: credit account, loans, session suspension and dialogue.
  Replace that integration with Gambling Den's token bank and safe once-only settlement.
- `cards/poker2/PokerGame.java`: heads-up community-card poker model and opponent AI.
  `poker5/` holds the multiplayer implementation. `pokerShared/` holds evaluators and AI tools.
- `gacha/CasinoGachaManager.java`: rotating ship prize pools and pity state. These mechanics
  should not be imported into the user's Slots or Pachinko games.

## Checks needed before a blackjack port

Do not assume that copying a working UI makes every rule correct. In the inspected model:

- `placeBet` checks available funds but does not reject zero or negative bets.
- If the last split hand busts, `handleBust` calls `determineWinners` without playing the
  dealer's turn for a surviving earlier split hand.
- A dealer natural and a player's multi-card 21 can reach the equal-total push branch.
  Natural-blackjack precedence needs a dedicated test.

Adapt and test the model with deterministic decks before connecting it to real tokens.
Keep the den's no-credit-purchases rule, mouse-only controls, independent game title, and
safe dialog-close behavior. Test split hands, doubled bets, pushes, natural blackjacks,
insufficient tokens, overflow and repeated callbacks. Rendering must not query GL state
from the app thread, because the user's Fast Rendering bridge stalls on those queries.
