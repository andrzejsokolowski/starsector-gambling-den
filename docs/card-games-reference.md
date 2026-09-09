# Interastral card-game reference

Inspected on 2026-09-09 at the user's request. Version 1.2.0 adapts the blackjack rules model
with token accounting and corrected settlement. The user explicitly requested this reuse.

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

## Implemented in 1.2.0

Blackjack uses six decks, an early dealer-blackjack check, soft-17 stands, and one split.
Split aces receive one card each. Bets use even token amounts so the 3:2 payout stays exact.
Closing stands on remaining hands and resolves the dealer once.

The table and card drawings use new code, with no imported artwork or asset dependencies.
`THIRD_PARTY_NOTICES.txt` contains the source attribution and original non-commercial code notice.
Tests cover the three source issues above, token accounting, split doubles, exits, and the graphics bridge.

## Card display in 1.3.1

The user rejected both the interface-font ranks and the custom vector ranks.
CardArt now follows CardSprites and renderCardFaceUp/renderCardFaceDown from CardRenderingUtils.
The deck uses unchanged upstream SVG-cards 2x PNG exports and keeps its aspect ratio.
The release includes the complete source SVGs, original author list, README, and LGPL-2.1 text.
Private asset paths avoid collisions with Interastral. No runtime casino dependency is needed.
The old CardRanks helper is removed. No card rank uses a Starsector LabelAPI.
